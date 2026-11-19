package br.com.aura.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import br.com.aura.store.security.AuraUser;
import br.com.aura.store.security.UserRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@SpringBootTest
@AutoConfigureMockMvc
class StoreIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired StoreService store;
    @Autowired UserRepository users;
    private AuraUser customer;

    @BeforeEach
    void reset() {
        db.update("DELETE FROM order_items");
        db.update("DELETE FROM orders");
        db.update("DELETE FROM users WHERE role = 'CUSTOMER'");
        db.update("DELETE FROM products WHERE id >= 100");
        db.update("UPDATE products SET stock = 10, active = TRUE");
        users.create("Cliente Teste", "cliente@example.com", "{noop}nao-usada", AuraUser.Role.CUSTOMER);
        customer = users.findByEmail("cliente@example.com").orElseThrow();
    }

    /** Pedido feito pelo cliente logado, com token CSRF, como o navegador faz. */
    private MockHttpServletRequestBuilder placeOrder() {
        return post("/api/orders").with(user(customer)).with(csrf()).contentType(APPLICATION_JSON);
    }

    private String order(String items) {
        return """
            {"name":"Cliente Teste","address":"Rua Teste, 123",
             "city":"São Paulo","zipCode":"01001-000","items":%s}
            """.formatted(items);
    }

    @Test void catalogIsSeeded() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].name").value("Headphone Wave"));
    }

    @Test void calculatesPricesAndPersistsSnapshot() throws Exception {
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.subtotal").value(349.90))
                .andExpect(jsonPath("$.shipping").value(19.90))
                .andExpect(jsonPath("$.total").value(369.80))
                .andExpect(jsonPath("$.status").value("DEMO_CONFIRMED"));
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=1", Integer.class)).isEqualTo(9);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT product_name FROM order_items", String.class)).isEqualTo("Headphone Wave");
        assertThat(db.queryForObject("SELECT email FROM orders", String.class)).isEqualTo("cliente@example.com");
        assertThat(db.queryForObject("SELECT user_id FROM orders", Long.class)).isEqualTo(customer.id());
    }

    @Test void grantsFreeShippingAndMergesDuplicateItems() throws Exception {
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1},{\"productId\":1,\"quantity\":1}]")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipping").value(0))
                .andExpect(jsonPath("$.total").value(699.80))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test void rollsBackAllChangesForUnavailableItem() throws Exception {
        db.update("UPDATE products SET stock=0 WHERE id=2");
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1},{\"productId\":2,\"quantity\":1}]")))
                .andExpect(status().isConflict());
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=1", Integer.class)).isEqualTo(10);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test void rejectsUnknownProductWithoutChangingStock() throws Exception {
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1},{\"productId\":999,\"quantity\":1}]")))
                .andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=1", Integer.class)).isEqualTo(10);
    }

    @Test void rejectsInvalidAndTamperedRequests() throws Exception {
        for (String items : List.of("[]", "[null]", "[{\"productId\":1,\"quantity\":0}]",
                "[{\"productId\":1,\"quantity\":-1}]", "[{\"productId\":1,\"quantity\":100}]")) {
            mvc.perform(placeOrder().content(order(items)))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1,\"price\":0.01}]")))
                .andExpect(status().isBadRequest());
        // O e-mail vem da conta: o cliente não pode enviá-lo (nem trocá-lo) no pedido.
        mvc.perform(placeOrder()
                        .content(order("[{\"productId\":1,\"quantity\":1}]").replace("\"name\"", "\"email\":\"outro@example.com\",\"name\"")))
                .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isZero();
    }

    @Test void concurrentCheckoutsCannotOversell() throws Exception {
        db.update("UPDATE products SET stock=1 WHERE id=1");
        var request = new CheckoutRequest("Teste", "Rua Teste, 1", "São Paulo",
                "01001000", List.of(new CheckoutRequest.Item(1, 1)));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = List.of(executor.submit(() -> attemptCheckout(start, request)),
                    executor.submit(() -> attemptCheckout(start, request)));
            start.countDown();
            assertThat(List.of(tasks.get(0).get(15, TimeUnit.SECONDS), tasks.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("SELECT stock FROM products WHERE id=1", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    private int attemptCheckout(CountDownLatch start, CheckoutRequest request) throws InterruptedException {
        start.await();
        try { store.checkout(customer, request); return 201; }
        catch (ResponseStatusException exception) { return exception.getStatusCode().value(); }
    }
}
