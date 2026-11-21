package br.com.aura.store.admin;

import br.com.aura.store.admin.AdminDtos.AdminOrder;
import br.com.aura.store.admin.AdminDtos.AdminProduct;
import br.com.aura.store.admin.AdminDtos.ProductRequest;
import br.com.aura.store.admin.AdminDtos.Summary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AdminService {
    private static final int LOW_STOCK = 5;
    private static final RowMapper<AdminProduct> PRODUCT = (rs, row) -> new AdminProduct(
            rs.getLong("id"), rs.getString("name"), rs.getString("category"), rs.getString("description"),
            rs.getBigDecimal("price"), rs.getBigDecimal("original_price"), rs.getString("image"),
            rs.getString("color"), rs.getString("badge"), rs.getInt("stock"), rs.getBoolean("active"));

    private final JdbcTemplate db;

    public AdminService(JdbcTemplate db) { this.db = db; }

    public Summary summary() {
        return db.queryForObject("""
                SELECT (SELECT COUNT(*) FROM products WHERE active) AS active_products,
                       (SELECT COUNT(*) FROM products WHERE active AND stock <= ?) AS low_stock,
                       (SELECT COUNT(*) FROM orders) AS orders,
                       (SELECT COALESCE(SUM(total), 0) FROM orders) AS revenue,
                       (SELECT COUNT(*) FROM users WHERE role = 'CUSTOMER') AS customers
                """, (rs, row) -> new Summary(rs.getInt("active_products"), rs.getInt("low_stock"),
                rs.getInt("orders"), rs.getBigDecimal("revenue"), rs.getInt("customers")), LOW_STOCK);
    }

    public List<AdminProduct> products() {
        return db.query("SELECT * FROM products ORDER BY active DESC, id", PRODUCT);
    }

    public AdminProduct product(long id) {
        return db.query("SELECT * FROM products WHERE id = ?", PRODUCT, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado."));
    }

    @Transactional
    public AdminProduct create(ProductRequest request) {
        validatePrices(request);
        long id = db.queryForObject("SELECT nextval('products_seq')", Long.class);
        db.update("""
                INSERT INTO products (id, name, category, description, price, original_price, image, color, badge, stock, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, request.name().strip(), request.category(), request.description().strip(), request.price(),
                request.originalPrice(), request.image(), request.color().strip(), blankToNull(request.badge()),
                request.stock(), request.active() == null || request.active());
        return product(id);
    }

    @Transactional
    public AdminProduct update(long id, ProductRequest request) {
        validatePrices(request);
        int changed = db.update("""
                UPDATE products SET name = ?, category = ?, description = ?, price = ?, original_price = ?,
                       image = ?, color = ?, badge = ?, stock = ?, active = ?
                WHERE id = ?
                """, request.name().strip(), request.category(), request.description().strip(), request.price(),
                request.originalPrice(), request.image(), request.color().strip(), blankToNull(request.badge()),
                request.stock(), request.active() == null || request.active(), id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        return product(id);
    }

    /** Exclusão lógica: o produto sai da loja, mas os pedidos antigos continuam íntegros. */
    @Transactional
    public void deactivate(long id) {
        if (db.update("UPDATE products SET active = FALSE WHERE id = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não encontrado.");
        }
    }

    public List<AdminOrder> orders() {
        return db.query("""
                SELECT o.id, o.created_at, o.customer_name, o.email, o.status, o.total,
                       (SELECT COALESCE(SUM(quantity), 0) FROM order_items i WHERE i.order_id = o.id) AS units
                FROM orders o ORDER BY o.created_at DESC
                """, (rs, row) -> new AdminOrder(rs.getObject("id", UUID.class), rs.getObject("created_at", OffsetDateTime.class),
                rs.getString("customer_name"), rs.getString("email"), rs.getString("status"),
                rs.getBigDecimal("total"), rs.getInt("units")));
    }

    private static void validatePrices(ProductRequest request) {
        if (request.originalPrice() != null && request.originalPrice().compareTo(request.price()) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O preço original deve ser maior que o preço de venda.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

}
