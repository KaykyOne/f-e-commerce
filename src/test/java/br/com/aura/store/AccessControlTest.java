package br.com.aura.store;

import br.com.aura.store.security.AuraUser;
import br.com.aura.store.security.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_HTML;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Perfis de acesso: visitante, cliente e administrador — páginas, API, cadastro, login e painel. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Controle de acesso e perfis")
class AccessControlTest {
    private static final String PASSWORD = "Senha1234";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;

    AuraUser customer;
    AuraUser admin;

    @BeforeEach
    void reset() {
        db.update("DELETE FROM order_items");
        db.update("DELETE FROM orders");
        db.update("DELETE FROM users WHERE role = 'CUSTOMER'");
        db.update("DELETE FROM products WHERE id >= 100");
        db.update("UPDATE products SET stock = 10, active = TRUE");
        customer = createCustomer("Ana Cliente", "ana@example.com");
        admin = users.findByEmail("admin@aura.dev").orElseThrow();
    }

    private AuraUser createCustomer(String name, String email) {
        users.create(name, email, encoder.encode(PASSWORD), AuraUser.Role.CUSTOMER);
        return users.findByEmail(email).orElseThrow();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    private static final String ORDER = """
            {"name":"Ana Cliente","address":"Rua A, 1","city":"São Paulo","zipCode":"01001-000",
             "items":[{"productId":1,"quantity":1}]}""";

    private static final String PRODUCT = """
            {"name":"Suporte Flow","category":"Setup","description":"Suporte de notebook em alumínio.",
             "price":99.90,"originalPrice":129.90,"image":"/images/keyboard.svg","color":"Prata",
             "badge":"Novidade","stock":7,"active":true}""";

    @Nested @DisplayName("Visitante")
    class Visitante {
        @Test void veOCatalogoESessaoAnonima() throws Exception {
            mvc.perform(get("/api/products")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(8));
            mvc.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
        }

        @Test void naoCompraSemEntrar() throws Exception {
            mvc.perform(post("/api/orders").with(csrf()).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isUnauthorized());
        }

        @Test void paginasProtegidasRedirecionamParaOLoginComDestino() throws Exception {
            mvc.perform(get("/conta").accept(TEXT_HTML))
                    .andExpect(status().isFound()).andExpect(redirectedUrl("/entrar?next=%2Fconta"));
            mvc.perform(get("/admin").accept(TEXT_HTML))
                    .andExpect(status().isFound()).andExpect(redirectedUrl("/entrar?next=%2Fadmin"));
        }

        @Test void apiRestritaResponde401() throws Exception {
            mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
            mvc.perform(get("/api/account/orders")).andExpect(status().isUnauthorized());
        }

        @Test void paginasPublicasAbrem() throws Exception {
            for (String page : new String[]{"/", "/entrar", "/cadastro"}) {
                mvc.perform(get(page).accept(TEXT_HTML)).andExpect(status().isOk());
            }
        }
    }

    @Nested @DisplayName("Cadastro e login")
    class CadastroELogin {
        @Test void cadastroCriaClienteJaLogado() throws Exception {
            var session = (MockHttpSession) mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Bruno\",\"email\":\"Bruno@Example.com\",\"password\":\"Bruno2026\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                    .andExpect(jsonPath("$.user.email").value("bruno@example.com"))
                    .andReturn().getRequest().getSession(false);
            mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.authenticated").value(true));
        }

        @Test void cadastroRejeitaDadosInvalidos() throws Exception {
            String[] invalid = {
                    "{\"name\":\"\",\"email\":\"x@example.com\",\"password\":\"Senha1234\"}",      // nome vazio
                    "{\"name\":\"X\",\"email\":\"nao-e-email\",\"password\":\"Senha1234\"}",       // e-mail inválido
                    "{\"name\":\"X\",\"email\":\"x@example.com\",\"password\":\"curta1\"}",         // senha curta
                    "{\"name\":\"X\",\"email\":\"x@example.com\",\"password\":\"somenteletras\"}",  // sem número
                    "{\"name\":\"X\",\"email\":\"x@example.com\",\"password\":\"Senha1234\",\"role\":\"ADMIN\"}", // tentar virar admin
            };
            for (String body : invalid) {
                mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
            assertThat(users.existsByEmail("x@example.com")).isFalse();
        }

        @Test void emailDuplicadoRetorna409() throws Exception {
            mvc.perform(post("/api/auth/register").with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Outra\",\"email\":\"ANA@example.com\",\"password\":\"Senha1234\"}"))
                    .andExpect(status().isConflict());
        }

        @Test void loginErradoNaoRevelaSeOEmailExiste() throws Exception {
            for (String email : new String[]{"ana@example.com", "ninguem@example.com"}) {
                mvc.perform(post("/api/auth/login").with(csrf()).contentType(APPLICATION_JSON)
                                .content("{\"email\":\"%s\",\"password\":\"errada123\"}".formatted(email)))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.message").value("E-mail ou senha incorretos."));
            }
        }

        @Test void loginELogout() throws Exception {
            MockHttpSession session = login("ana@example.com", PASSWORD);
            mvc.perform(get("/api/auth/me").session(session))
                    .andExpect(jsonPath("$.user.name").value("Ana Cliente"));
            mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
            mvc.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.authenticated").value(false));
        }

        @Test void semTokenCsrfARequisicaoEBloqueada() throws Exception {
            mvc.perform(post("/api/orders").with(user(customer)).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested @DisplayName("Cliente")
    class Cliente {
        @Test void compraEVeSomenteOsPropriosPedidos() throws Exception {
            AuraUser other = createCustomer("Caio", "caio@example.com");
            mvc.perform(post("/api/orders").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/orders").with(user(other)).with(csrf()).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isCreated());

            mvc.perform(get("/api/account/orders").with(user(customer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].items[0].name").value("Headphone Wave"));
        }

        @Test void atualizaPerfil() throws Exception {
            mvc.perform(put("/api/account").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Ana Souza\",\"phone\":\"(11) 98888-7777\",\"address\":\"Rua B, 2\",\"city\":\"Campinas\",\"zipCode\":\"13010-000\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Ana Souza"))
                    .andExpect(jsonPath("$.city").value("Campinas"));
            mvc.perform(put("/api/account").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"name\":\"Ana\",\"zipCode\":\"123\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test void trocaSenhaExigindoASenhaAtual() throws Exception {
            mvc.perform(put("/api/account/password").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"currentPassword\":\"errada123\",\"newPassword\":\"NovaSenha99\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/account/password").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON)
                            .content("{\"currentPassword\":\"%s\",\"newPassword\":\"NovaSenha99\"}".formatted(PASSWORD)))
                    .andExpect(status().isNoContent());
            login("ana@example.com", "NovaSenha99");
        }

        @Test void naoAcessaOPainelAdministrativo() throws Exception {
            mvc.perform(get("/api/admin/products").with(user(customer))).andExpect(status().isForbidden());
            mvc.perform(post("/api/admin/products").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/admin").with(user(customer)).accept(TEXT_HTML)).andExpect(status().isForbidden());
        }
    }

    @Nested @DisplayName("Administrador")
    class Administrador {
        @Test void abreOPainelEVeOResumo() throws Exception {
            mvc.perform(get("/admin").with(user(admin)).accept(TEXT_HTML)).andExpect(status().isOk());
            mvc.perform(get("/api/admin/summary").with(user(admin)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activeProducts").value(8))
                    .andExpect(jsonPath("$.customers").value(1));
        }

        @Test void cadastraEditaEDesativaProduto() throws Exception {
            String body = mvc.perform(post("/api/admin/products").with(user(admin)).with(csrf()).contentType(APPLICATION_JSON).content(PRODUCT))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Suporte Flow"))
                    .andReturn().getResponse().getContentAsString();
            long id = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
            mvc.perform(get("/api/products")).andExpect(jsonPath("$.length()").value(9));

            mvc.perform(put("/api/admin/products/" + id).with(user(admin)).with(csrf()).contentType(APPLICATION_JSON)
                            .content(PRODUCT.replace("99.90", "89.90")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.price").value(89.90));

            mvc.perform(delete("/api/admin/products/" + id).with(user(admin)).with(csrf())).andExpect(status().isNoContent());
            mvc.perform(get("/api/products")).andExpect(jsonPath("$.length()").value(8));
            mvc.perform(get("/api/admin/products").with(user(admin)))
                    .andExpect(jsonPath("$[?(@.id == %d)].active".formatted(id)).value(false));
            // Produto desativado não pode mais ser comprado.
            mvc.perform(post("/api/orders").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON)
                            .content(ORDER.replace("\"productId\":1", "\"productId\":" + id)))
                    .andExpect(status().isNotFound());
        }

        @Test void rejeitaProdutoInvalido() throws Exception {
            for (String body : new String[]{
                    PRODUCT.replace("\"Suporte Flow\"", "\"\""),               // nome vazio
                    PRODUCT.replace("\"price\":99.90", "\"price\":-1"),        // preço negativo
                    PRODUCT.replace("\"stock\":7", "\"stock\":-3"),            // estoque negativo
                    PRODUCT.replace("\"Setup\"", "\"Eletrodomésticos\""),      // categoria inexistente
                    PRODUCT.replace("129.90", "50.00"),                        // preço original menor que o de venda
                    PRODUCT.replace("/images/keyboard.svg", "https://site.com/x.png")}) {
                mvc.perform(post("/api/admin/products").with(user(admin)).with(csrf()).contentType(APPLICATION_JSON).content(body))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test void veTodosOsPedidosENaoCompra() throws Exception {
            mvc.perform(post("/api/orders").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isCreated());
            mvc.perform(get("/api/admin/orders").with(user(admin)))
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].email").value("ana@example.com"));
            mvc.perform(post("/api/orders").with(user(admin)).with(csrf()).contentType(APPLICATION_JSON).content(ORDER))
                    .andExpect(status().isForbidden());
        }
    }
}
