package br.com.aura.store;

import br.com.aura.store.security.AuraUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.*;

@Service
public class StoreService {
    private final JdbcTemplate db;
    private static final BigDecimal FREE_SHIPPING = new BigDecimal("399.00");
    private static final BigDecimal SHIPPING = new BigDecimal("19.90");
    private static final RowMapper<Product> PRODUCT = (rs, row) -> new Product(
            rs.getLong("id"), rs.getString("name"), rs.getString("category"),
            rs.getString("description"), rs.getBigDecimal("price"), rs.getBigDecimal("original_price"),
            rs.getString("image"), rs.getString("color"), rs.getString("badge"), rs.getInt("stock"));

    public StoreService(JdbcTemplate db) { this.db = db; }

    public List<Product> products() {
        return db.query("SELECT * FROM products WHERE active ORDER BY id", PRODUCT);
    }

    @Transactional
    public OrderReceipt checkout(AuraUser customer, CheckoutRequest request) {
        // Lock in a stable order so concurrent checkouts cannot oversell or deadlock each other.
        Map<Long, Integer> quantities = new TreeMap<>();
        request.items().forEach(item -> quantities.merge(item.productId(), item.quantity(), Integer::sum));
        List<OrderReceipt.Line> lines = new ArrayList<>();
        BigDecimal subtotal = new BigDecimal("0.00");
        for (var entry : quantities.entrySet()) {
            if (entry.getValue() > 99) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limite de 99 unidades por produto.");
            }
            var found = db.query("SELECT * FROM products WHERE id = ? AND active FOR UPDATE", PRODUCT, entry.getKey());
            if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
            Product product = found.getFirst();
            if (product.stock() < entry.getValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente para " + product.name() + ". Atualize o carrinho.");
            }
            lines.add(new OrderReceipt.Line(product.id(), product.name(), entry.getValue(), product.price()));
            subtotal = subtotal.add(product.price().multiply(BigDecimal.valueOf(entry.getValue())));
            db.update("UPDATE products SET stock = stock - ? WHERE id = ?", entry.getValue(), product.id());
        }
        BigDecimal shipping = subtotal.compareTo(FREE_SHIPPING) >= 0 ? new BigDecimal("0.00") : SHIPPING;
        UUID id = UUID.randomUUID();
        db.update("""
                INSERT INTO orders (id, user_id, customer_name, email, address, city, zip_code, status, subtotal, shipping, total)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'DEMO_CONFIRMED', ?, ?, ?)
                """, id, customer.id(), request.name().strip(), customer.email(), request.address().strip(),
                request.city().strip(), request.zipCode(), subtotal, shipping, subtotal.add(shipping));
        for (var line : lines) {
            db.update("INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price) VALUES (?, ?, ?, ?, ?)",
                    id, line.productId(), line.name(), line.quantity(), line.unitPrice());
        }
        return new OrderReceipt(id, "DEMO_CONFIRMED", subtotal, shipping, subtotal.add(shipping), List.copyOf(lines));
    }
}
