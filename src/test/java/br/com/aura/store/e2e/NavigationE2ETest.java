package br.com.aura.store.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.ReducedMotion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import br.com.aura.store.security.AuraUser;
import br.com.aura.store.security.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Path;
import java.text.Normalizer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tema 14 — Teste de interface: navegação.
 *
 * <p>Sobe a aplicação real (Spring Boot em porta aleatória + banco H2 de teste) e controla
 * um navegador de verdade com Playwright, clicando em links, botões e menus como um usuário.
 *
 * <p>Propriedades opcionais: {@code -Dbrowser=chromium|firefox|webkit},
 * {@code -Dbrowser.channel=chrome}, {@code -Dheaded=true}, {@code -DslowMo=400}.
 */
@Tag("e2e")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Tema 14 — Navegação da loja Aura")
class NavigationE2ETest {

    private static final Path EVIDENCIAS = Paths.get("target", "evidencias", "navegacao");

    /** Contas usadas nos testes. O administrador é criado na inicialização (application.properties de teste). */
    private static final String CLIENTE = "cliente.e2e@aura.dev", SENHA_CLIENTE = "Cliente2026";
    private static final String ADMIN = "admin@aura.dev", SENHA_ADMIN = "AuraAdmin2026";

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;
    private final List<String> errosNoConsole = new ArrayList<>();
    private final List<String> requisicoesComFalha = new ArrayList<>();

    @BeforeAll
    void abrirNavegador() {
        playwright = Playwright.create();
        BrowserType tipo = switch (System.getProperty("browser", "chromium")) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
        BrowserType.LaunchOptions opcoes = new BrowserType.LaunchOptions()
                .setHeadless(!Boolean.getBoolean("headed"))
                .setSlowMo(Double.parseDouble(System.getProperty("slowMo", "0")));
        String canal = System.getProperty("browser.channel", "");
        if (!canal.isBlank()) opcoes.setChannel(canal);
        browser = tipo.launch(opcoes);
    }

    @AfterAll
    void fecharNavegador() {
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void bancoConhecido() {
        db.update("DELETE FROM order_items");
        db.update("DELETE FROM orders");
        db.update("DELETE FROM users WHERE role = 'CUSTOMER'");
        db.update("DELETE FROM products WHERE id >= 100");
        db.update("UPDATE products SET active = TRUE, stock = 20");
        long id = users.create("Ana Cliente", CLIENTE, encoder.encode(SENHA_CLIENTE), AuraUser.Role.CUSTOMER);
        db.update("UPDATE users SET address = 'Rua Exemplo, 123', city = 'São Paulo', zip_code = '01001-000' WHERE id = ?", id);
    }

    @BeforeEach
    void novaAba() {
        // Contexto novo por teste = navegador "limpo" (sem sacola salva no localStorage).
        context = browser.newContext(new Browser.NewContextOptions()
                .setViewportSize(1280, 800)
                .setReducedMotion(ReducedMotion.REDUCE)
                .setBaseURL("http://localhost:" + port));
        page = context.newPage();
        errosNoConsole.clear();
        requisicoesComFalha.clear();
        page.onConsoleMessage(msg -> { if ("error".equals(msg.type())) errosNoConsole.add(msg.text()); });
        page.onPageError(erro -> errosNoConsole.add(erro));
        page.onResponse(resposta -> { if (resposta.status() >= 400) requisicoesComFalha.add(resposta.status() + " " + resposta.url()); });
    }

    @AfterEach
    void registrarEvidencia(TestInfo info) {
        String nome = Normalizer.normalize(info.getDisplayName(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "").toLowerCase();
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(EVIDENCIAS.resolve(System.getProperty("browser", "chromium")).resolve(nome + ".png")));
        context.close();
    }

    private void abrirLoja() {
        page.navigate("/");
        assertThat(page.locator(".product-card")).hasCount(8);
    }

    private void entrar(String email, String senha) {
        page.getByLabel("E-mail").fill(email);
        page.getByLabel("Senha").fill(senha);
        page.locator("#login-form").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Entrar")).click();
    }

    private void entrarComo(String email, String senha, String destinoEsperado) {
        page.navigate("/entrar");
        entrar(email, senha);
        assertThat(page).hasURL(Pattern.compile(":" + port + destinoEsperado + "$"));
        // A URL muda antes de a página terminar de carregar; navegar nesse meio-tempo é abortado no Firefox.
        page.waitForLoadState(LoadState.NETWORKIDLE);
    }

    private Locator linkDaConta() {
        return page.locator("#account-link");
    }

    private Locator menu() {
        return page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Menu principal"));
    }

    // ------------------------------------------------------------------ Página inicial

    @Test @Order(1)
    @DisplayName("Página inicial carrega com menu, catálogo e sem erros")
    void paginaInicialCarrega() {
        Response resposta = page.navigate("/");

        assertThat(resposta.status()).isEqualTo(200);
        assertThat(page).hasTitle("Aura — Tecnologia no seu ritmo");
        assertThat(menu().getByRole(AriaRole.LINK)).hasText(new String[]{"Explorar", "Áudio", "Seu setup", "Sobre a Aura"});
        assertThat(page.locator(".product-card")).hasCount(8);
        assertThat(page.locator("#result-count")).hasText("8 essenciais para o seu dia");
        assertThat(errosNoConsole).as("erros de JavaScript").isEmpty();
        assertThat(requisicoesComFalha).as("CSS, JS, imagens e API devem responder sem erro").isEmpty();
    }

    @Test @Order(2)
    @DisplayName("Nenhum link quebrado: toda âncora aponta para uma seção existente")
    void nenhumLinkQuebrado() {
        abrirLoja();
        @SuppressWarnings("unchecked")
        List<String> hrefs = (List<String>) page.evaluate(
                "() => [...document.querySelectorAll('a[href]')].map(a => a.getAttribute('href'))");

        assertThat(hrefs).isNotEmpty();
        for (String href : hrefs) {
            if (href.startsWith("#")) {
                assertThat(page.locator(href).count()).as("destino do link " + href).isEqualTo(1);
            } else {
                assertThat(page.request().get(href).status()).as("link " + href).isEqualTo(200);
            }
        }
    }

    // ------------------------------------------------------------------ Menu principal

    @Test @Order(3)
    @DisplayName("Menu 'Explorar' leva ao catálogo")
    void menuExplorarLevaAoCatalogo() {
        abrirLoja();
        menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Explorar")).click();

        assertThat(page).hasURL(Pattern.compile("#catalogo$"));
        assertThat(page.locator("#catalog-title")).isInViewport();
    }

    @ParameterizedTest(name = "Menu ''{0}'' filtra a categoria {1} ({2} produtos)") @Order(4)
    @CsvSource({"Áudio, Áudio, 3", "Seu setup, Setup, 2"})
    @DisplayName("Atalhos do menu filtram o catálogo pela categoria")
    void menuDeCategoriaFiltraCatalogo(String link, String categoria, int quantidade) {
        abrirLoja();
        page.locator("#search").fill("algo digitado antes");
        menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(link)).click();

        assertThat(page).hasURL(Pattern.compile("#catalogo$"));
        assertThat(page.locator("#search")).hasValue("");            // o atalho limpa a busca
        assertThat(page.locator("[data-category='" + categoria + "']")).hasAttribute("aria-pressed", "true");
        assertThat(page.locator(".product-card")).hasCount(quantidade);
        assertThat(page.locator(".product-meta > span:first-child")).hasText(repetir(categoria, quantidade));
    }

    @Test @Order(5)
    @DisplayName("Menu 'Sobre a Aura' leva à seção institucional, que volta ao catálogo")
    void menuSobreEVoltaAoCatalogo() {
        abrirLoja();
        menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Sobre a Aura")).click();
        assertThat(page).hasURL(Pattern.compile("#sobre$"));
        assertThat(page.locator("#sobre h2")).isInViewport();

        page.locator("#sobre").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Explore a coleção")).click();
        assertThat(page).hasURL(Pattern.compile("#catalogo$"));
        assertThat(page.locator("#catalog-title")).isInViewport();
    }

    // ------------------------------------------------------------------ Botões e links da página

    @Test @Order(6)
    @DisplayName("Chamadas do banner (botão e cartão do produto) levam ao catálogo")
    void chamadasDoBannerLevamAoCatalogo() {
        abrirLoja();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Encontre seu próximo favorito")).click();
        assertThat(page.locator("#catalog-title")).isInViewport();

        page.evaluate("() => window.scrollTo(0, 0)");
        page.locator(".hero-product-card").click();
        assertThat(page).hasURL(Pattern.compile("#catalogo$"));
        assertThat(page.locator("#catalog-title")).isInViewport();
    }

    @Test @Order(7)
    @DisplayName("Rodapé 'Voltar ao topo' retorna ao início da página")
    void rodapeVoltaAoTopo() {
        abrirLoja();
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar ao topo")).click();

        assertThat(page).hasURL(Pattern.compile("#hero-title$"));
        assertThat(page.locator("#hero-title")).isInViewport();
        assertThat(page.locator(".footer")).not().isInViewport();
    }

    @Test @Order(8)
    @DisplayName("Logo redireciona para a página inicial ('/') a partir de qualquer seção")
    void logoRedirecionaParaInicio() {
        page.navigate("/#sobre");
        assertThat(page.locator("#sobre h2")).isInViewport();

        page.locator(".header .logo").click();

        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));
        assertThat(page.locator("#hero-title")).isInViewport();
    }

    @Test @Order(9)
    @DisplayName("Link direto (deep link) abre a página já na seção indicada")
    void linkDiretoAbreNaSecao() {
        page.navigate("/#sobre");
        assertThat(page.locator("#sobre h2")).isInViewport();
        assertThat(page.locator("#hero-title")).not().isInViewport();
    }

    @Test @Order(10)
    @DisplayName("Botões Voltar/Avançar do navegador respeitam o histórico de navegação")
    void historicoDoNavegador() {
        abrirLoja();
        menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Sobre a Aura")).click();
        assertThat(page).hasURL(Pattern.compile("#sobre$"));
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar ao topo")).click();
        assertThat(page).hasURL(Pattern.compile("#hero-title$"));

        page.goBack();
        assertThat(page).hasURL(Pattern.compile("#sobre$"));
        assertThat(page.locator("#sobre h2")).isInViewport();

        page.goForward();
        assertThat(page).hasURL(Pattern.compile("#hero-title$"));
        assertThat(page.locator("#hero-title")).isInViewport();
    }

    @Test @Order(11)
    @DisplayName("Navegação por teclado: 'Pular para os produtos' é o primeiro item e leva ao catálogo")
    void navegacaoPorTeclado() {
        abrirLoja();
        page.keyboard().press("Tab");
        Locator pular = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Pular para os produtos"));
        assertThat(pular).isFocused();
        assertThat(pular).isInViewport();

        page.keyboard().press("Enter");
        assertThat(page).hasURL(Pattern.compile("#catalogo$"));
        assertThat(page.locator("#catalog-title")).isInViewport();
    }

    // ------------------------------------------------------------------ Filtros (menu de categorias)

    @ParameterizedTest(name = "Botão ''{0}'' mostra {1} produto(s)") @Order(12)
    @CsvSource({"Áudio, 3", "Wearables, 1", "Setup, 2", "Lifestyle, 1", "Acessórios, 1"})
    @DisplayName("Botões de categoria filtram e 'Todos' restaura o catálogo")
    void botoesDeCategoria(String categoria, int quantidade) {
        abrirLoja();
        Locator botao = page.locator("[data-category='" + categoria + "']");
        botao.click();
        assertThat(botao).hasAttribute("aria-pressed", "true");
        assertThat(page.locator(".product-card")).hasCount(quantidade);

        page.locator("[data-category='Todos']").click();
        assertThat(page.locator(".product-card")).hasCount(8);
    }

    // ------------------------------------------------------------------ Janelas (detalhes, sacola, checkout)

    @Test @Order(13)
    @DisplayName("Detalhes do produto abrem pelo nome e fecham pelo X e pela tecla Esc")
    void detalhesDoProduto() {
        abrirLoja();
        Locator detalhes = page.locator("#product-dialog");

        page.locator(".product-name", new Page.LocatorOptions().setHasText("Speaker Mini")).click();
        assertThat(detalhes).isVisible();
        assertThat(detalhes.locator("#detail-name")).hasText("Speaker Mini");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Fechar detalhes")).click();
        assertThat(detalhes).isHidden();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ver detalhes de Mouse Move")).click();
        assertThat(detalhes.locator("#detail-name")).hasText("Mouse Move");
        page.keyboard().press("Escape");
        assertThat(detalhes).isHidden();
    }

    @Test @Order(14)
    @DisplayName("Sacola vazia oferece o caminho de volta para a coleção")
    void sacolaVaziaVoltaParaColecao() {
        abrirLoja();
        page.locator("#open-cart").click();
        Locator sacola = page.locator("#cart-dialog");
        assertThat(sacola).isVisible();
        assertThat(sacola).containsText("Sua sacola ainda está vazia");

        sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Explorar a coleção")).click();
        assertThat(sacola).isHidden();
    }

    @Test @Order(15)
    @DisplayName("Visitante que tenta finalizar a compra vai ao login e volta com a sacola aberta")
    void visitanteVaiAoLoginEVoltaParaASacola() {
        abrirLoja();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Adicionar Teclado Type à sacola")).click();
        page.locator("#open-cart").click();

        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Entrar para finalizar")).click();
        assertThat(page).hasURL(Pattern.compile("/entrar\\?next=%2F%23sacola$"));
        assertThat(page.locator("#login-reason")).isVisible();

        entrar(CLIENTE, SENHA_CLIENTE);
        assertThat(page).hasURL(Pattern.compile(":" + port + "/#sacola$"));
        Locator sacola = page.locator("#cart-dialog");
        assertThat(sacola).isVisible();
        assertThat(sacola).containsText("Teclado Type");                       // a sacola foi preservada
        assertThat(sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ir para o checkout"))).isVisible();
    }

    @Test @Order(16)
    @DisplayName("Cliente compra: sacola → checkout → voltar → confirmação → Meus pedidos")
    void fluxoDeCompraDoCliente() {
        entrarComo(CLIENTE, SENHA_CLIENTE, "/conta");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Continuar comprando")).click();
        assertThat(page).hasURL(Pattern.compile(":" + port + "/#catalogo$"));

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Adicionar Mouse Move à sacola")).click();
        assertThat(page.locator("#cart-count")).hasText("1");
        page.locator("#open-cart").click();
        Locator sacola = page.locator("#cart-dialog");
        sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ir para o checkout")).click();

        Locator checkout = page.locator("#checkout-dialog");
        assertThat(checkout).isVisible();
        assertThat(page.locator("#checkout-email")).hasText(CLIENTE);
        assertThat(page.getByLabel("Nome completo")).hasValue("Ana Cliente");   // preenchido pelo perfil
        assertThat(page.getByLabel("CEP")).hasValue("01001-000");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("← Voltar para a sacola")).click();
        assertThat(sacola).isVisible();
        assertThat(checkout).isHidden();
        sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ir para o checkout")).click();
        page.locator("#submit-order").click();

        Locator sucesso = page.locator("#success-dialog");
        assertThat(sucesso).containsText("Pedido registrado");
        assertThat(page.locator("#cart-count")).hasText("0");
        sucesso.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Ver meus pedidos")).click();

        assertThat(page).hasURL(Pattern.compile("/conta#pedidos$"));
        assertThat(page.locator("#orders .order-card")).hasCount(1);
        assertThat(page.locator("#orders")).containsText("Mouse Move");
    }

    // ------------------------------------------------------------------ Rotas inexistentes e celular

    @Test @Order(17)
    @DisplayName("Rota inexistente mostra página 404 da loja com caminho de volta ao início")
    void rotaInexistenteRedirecionaParaLoja() {
        Response resposta = page.navigate("/pagina-que-nao-existe");

        assertThat(resposta.status()).isEqualTo(404);
        assertThat(page).hasTitle(Pattern.compile("Página não encontrada"));
        Locator voltar = page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar para a loja"));
        assertThat(voltar).isVisible();
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Ver produtos"))).hasAttribute("href", "/#catalogo");
        page.screenshot(new Page.ScreenshotOptions()
                .setPath(EVIDENCIAS.resolve(System.getProperty("browser", "chromium")).resolve("pagina-404.png")));

        voltar.click();
        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));
        assertThat(page.locator(".product-card")).hasCount(8);
    }

    @Test @Order(18)
    @DisplayName("No celular, o menu mantém 'Explorar', 'Sobre' e a sacola funcionando")
    void navegacaoNoCelular() {
        page.setViewportSize(390, 844);
        abrirLoja();

        assertThat(menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Explorar"))).isVisible();
        assertThat(menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Sobre a Aura"))).isVisible();
        assertThat(menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Áudio"))).isHidden();

        menu().getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Sobre a Aura")).click();
        assertThat(page.locator("#sobre h2")).isInViewport();

        page.locator("#open-cart").click();
        assertThat(page.locator("#cart-dialog")).isVisible();
        assertThat(page.locator("#cart-dialog")).isInViewport();
        page.keyboard().press("Escape");

        assertThat(linkDaConta()).isVisible();                                   // ícone de conta no celular
        linkDaConta().click();
        assertThat(page).hasURL(Pattern.compile("/entrar$"));
    }

    // ------------------------------------------------------------------ Perfis: visitante, cliente e administrador

    @Test @Order(19)
    @DisplayName("Link de conta no cabeçalho muda conforme o perfil: Entrar, Minha conta, Painel admin")
    void linkDaContaPorPerfil() {
        abrirLoja();
        assertThat(linkDaConta()).hasAttribute("aria-label", "Entrar");
        assertThat(linkDaConta()).hasAttribute("href", "/entrar");

        entrarComo(CLIENTE, SENHA_CLIENTE, "/conta");
        page.navigate("/");
        assertThat(linkDaConta()).hasAttribute("aria-label", "Minha conta");
        linkDaConta().click();
        assertThat(page).hasURL(Pattern.compile("/conta$"));

        page.locator(".side-nav").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Sair")).click();
        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));
        entrarComo(ADMIN, SENHA_ADMIN, "/admin");
        page.navigate("/");
        assertThat(linkDaConta()).hasAttribute("aria-label", "Painel admin");
        linkDaConta().click();
        assertThat(page).hasURL(Pattern.compile("/admin$"));
    }

    @Test @Order(20)
    @DisplayName("Entrar e Criar conta se ligam e preservam o destino (?next)")
    void entrarECadastroSeLigam() {
        page.navigate("/entrar?next=%2Fconta%23pedidos");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Criar conta")).click();
        assertThat(page).hasURL(Pattern.compile("/cadastro\\?next=%2Fconta%23pedidos$"));
        assertThat(page.locator("#page-title")).hasText("Criar conta.");

        page.locator(".auth-switch").getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Entrar")).click();
        assertThat(page).hasURL(Pattern.compile("/entrar\\?next=%2Fconta%23pedidos$"));
    }

    @Test @Order(21)
    @DisplayName("Cadastro cria a conta de cliente e leva para Minha conta")
    void cadastroLevaParaAConta() {
        page.navigate("/cadastro");
        page.getByLabel("Nome completo").fill("Bruno Novo");
        page.getByLabel("E-mail").fill("bruno.e2e@aura.dev");
        page.getByLabel("Senha", new Page.GetByLabelOptions().setExact(true)).fill("Bruno2026");
        page.getByLabel("Confirmar senha").fill("Bruno2026");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Criar minha conta")).click();

        assertThat(page).hasURL(Pattern.compile("/conta$"));
        assertThat(page.locator("#greeting")).hasText("Olá, Bruno.");
        assertThat(page.locator("#orders")).containsText("Nenhum pedido ainda");
        assertThat(linkDaConta()).hasAttribute("aria-label", "Minha conta");
    }

    @Test @Order(22)
    @DisplayName("Página protegida redireciona o visitante ao login e volta para ela depois de entrar")
    void paginaProtegidaRedirecionaEVolta() {
        Response resposta = page.navigate("/conta");
        assertThat(page).hasURL(Pattern.compile("/entrar\\?next=%2Fconta$"));
        assertThat(resposta.request().redirectedFrom()).isNotNull();             // redirecionamento feito pelo servidor

        entrar(CLIENTE, SENHA_CLIENTE);
        assertThat(page).hasURL(Pattern.compile(":" + port + "/conta$"));
        assertThat(page.locator("#greeting")).hasText("Olá, Ana.");
    }

    @Test @Order(23)
    @DisplayName("Cliente que tenta abrir o painel admin recebe 403 com caminho de volta")
    void clienteNoPainelRecebe403() {
        entrarComo(CLIENTE, SENHA_CLIENTE, "/conta");
        Response resposta = page.navigate("/admin");

        assertThat(resposta.status()).isEqualTo(403);
        assertThat(page).hasTitle("Acesso restrito — Aura");
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Voltar para a loja")).click();
        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));

        page.navigate("/admin");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar com outra conta")).click();
        assertThat(page).hasURL(Pattern.compile("/entrar$"));
        entrar(ADMIN, SENHA_ADMIN);
        assertThat(page).hasURL(Pattern.compile("/admin$"));
    }

    @Test @Order(24)
    @DisplayName("Painel admin: menu lateral, Ver loja e a sacola não permite compra do administrador")
    void navegacaoNoPainelAdmin() {
        entrarComo(ADMIN, SENHA_ADMIN, "/admin");
        assertThat(page.locator("#stats .stat")).hasCount(5);
        Locator lateral = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Seções do painel"));

        for (String[] secao : new String[][]{{"Produtos", "produtos"}, {"Pedidos", "pedidos"}, {"Resumo", "resumo"}}) {
            lateral.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(secao[0])).click();
            assertThat(page).hasURL(Pattern.compile("#" + secao[1] + "$"));
            assertThat(page.locator("#" + secao[1] + " h2")).isInViewport();
        }

        lateral.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName("Ver loja")).click();
        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Adicionar Mouse Move à sacola")).click();
        page.locator("#open-cart").click();
        assertThat(page.locator("#cart-dialog .admin-note")).isVisible();
        assertThat(page.locator("#go-checkout")).hasCount(0);
    }

    @Test @Order(25)
    @DisplayName("Admin cadastra produto, que aparece na loja; ao desativar, some da loja")
    void adminCadastraEDesativaProduto() {
        entrarComo(ADMIN, SENHA_ADMIN, "/admin");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Novo produto")).click();
        Locator formulario = page.locator("#product-dialog");
        assertThat(formulario).isVisible();
        formulario.getByLabel("Nome").fill("Suporte Flow");
        formulario.getByLabel("Categoria").selectOption("Setup");
        formulario.getByLabel("Cor").fill("Prata");
        formulario.getByLabel("Descrição").fill("Suporte de notebook em alumínio.");
        formulario.getByLabel("Preço (R$)").fill("99.90");
        formulario.getByLabel("Estoque").fill("7");
        formulario.getByLabel("Imagem").selectOption("/images/keyboard.svg");
        formulario.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Salvar produto")).click();

        assertThat(formulario).isHidden();
        assertThat(page.locator("#product-rows")).containsText("Suporte Flow");

        page.navigate("/");
        assertThat(page.locator(".product-card")).hasCount(9);
        assertThat(page.locator(".product-name", new Page.LocatorOptions().setHasText("Suporte Flow"))).isVisible();

        linkDaConta().click();
        assertThat(page).hasURL(Pattern.compile("/admin$"));
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Desativar Suporte Flow")).click();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Reativar Suporte Flow"))).isVisible();
        page.navigate("/");
        assertThat(page.locator(".product-card")).hasCount(8);
    }

    @Test @Order(26)
    @DisplayName("Minha conta: menu lateral leva a Perfil, Pedidos e Segurança; perfil salvo aparece no cabeçalho")
    void navegacaoNaContaDoCliente() {
        entrarComo(CLIENTE, SENHA_CLIENTE, "/conta");
        Locator lateral = page.getByRole(AriaRole.NAVIGATION, new Page.GetByRoleOptions().setName("Seções da conta"));
        for (String[] secao : new String[][]{{"Segurança", "seguranca"}, {"Meus pedidos", "pedidos"}, {"Perfil", "perfil"}}) {
            lateral.getByRole(AriaRole.LINK, new Locator.GetByRoleOptions().setName(secao[0])).click();
            assertThat(page).hasURL(Pattern.compile("#" + secao[1] + "$"));
            assertThat(page.locator("#" + secao[1] + " h2")).isInViewport();
        }

        page.getByLabel("Nome completo").fill("Ana Souza");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salvar perfil")).click();
        assertThat(page.locator("#toast")).hasText("Perfil atualizado.");
        assertThat(page.locator("#greeting")).hasText("Olá, Ana.");

        page.reload();
        assertThat(page.getByLabel("Nome completo")).hasValue("Ana Souza");
    }

    @Test @Order(27)
    @DisplayName("Sair encerra a sessão: volta à loja e a conta passa a exigir login de novo")
    void sairEncerraASessao() {
        entrarComo(CLIENTE, SENHA_CLIENTE, "/conta");
        page.locator(".side-nav").getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Sair")).click();

        assertThat(page).hasURL(Pattern.compile(":" + port + "/$"));
        assertThat(linkDaConta()).hasAttribute("aria-label", "Entrar");
        page.navigate("/conta");
        assertThat(page).hasURL(Pattern.compile("/entrar\\?next=%2Fconta$"));
    }

    @Test @Order(28)
    @DisplayName("Quem já está logado e abre /entrar vai direto para a sua área")
    void logadoNaoVeOLoginDeNovo() {
        entrarComo(ADMIN, SENHA_ADMIN, "/admin");
        page.navigate("/entrar");
        assertThat(page).hasURL(Pattern.compile("/admin$"));
    }

    private static String[] repetir(String valor, int vezes) {
        String[] valores = new String[vezes];
        java.util.Arrays.fill(valores, valor);
        return valores;
    }
}
