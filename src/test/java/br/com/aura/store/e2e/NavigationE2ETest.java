package br.com.aura.store.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
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
import org.springframework.boot.test.context.SpringBootTest;
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

    @LocalServerPort int port;

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
    @DisplayName("Fluxo completo: produto → sacola → checkout → voltar → checkout → confirmação → loja")
    void fluxoSacolaCheckoutConfirmacao() {
        abrirLoja();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Adicionar Teclado Type à sacola")).click();
        assertThat(page.locator("#cart-count")).hasText("1");
        assertThat(page.locator("#open-cart")).hasAttribute("aria-label", "Abrir carrinho, 1 item");

        page.locator("#open-cart").click();
        Locator sacola = page.locator("#cart-dialog");
        assertThat(sacola).containsText("Teclado Type");

        sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ir para o checkout")).click();
        Locator checkout = page.locator("#checkout-dialog");
        assertThat(checkout).isVisible();
        assertThat(sacola).isHidden();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("← Voltar para a sacola")).click();
        assertThat(sacola).isVisible();
        assertThat(checkout).isHidden();

        sacola.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Ir para o checkout")).click();
        page.getByLabel("Nome completo").fill("Cliente de Teste");
        page.getByLabel("E-mail").fill("teste@example.com");
        page.getByLabel("Endereço e número").fill("Rua Exemplo, 123");
        page.getByLabel("Cidade").fill("São Paulo");
        page.getByLabel("CEP").fill("01001-000");
        page.locator("#submit-order").click();

        Locator sucesso = page.locator("#success-dialog");
        assertThat(sucesso).isVisible();
        assertThat(sucesso).containsText("Pedido registrado");
        sucesso.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Continuar explorando")).click();
        assertThat(sucesso).isHidden();
        assertThat(page.locator("#cart-count")).hasText("0");
        assertThat(page.locator(".product-card")).hasCount(8);
    }

    // ------------------------------------------------------------------ Rotas inexistentes e celular

    @Test @Order(16)
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

    @Test @Order(17)
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
    }

    private static String[] repetir(String valor, int vezes) {
        String[] valores = new String[vezes];
        java.util.Arrays.fill(valores, valor);
        return valores;
    }
}
