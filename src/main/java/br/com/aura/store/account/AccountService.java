package br.com.aura.store.account;

import br.com.aura.store.account.AccountDtos.OrderSummary;
import br.com.aura.store.account.AccountDtos.PasswordRequest;
import br.com.aura.store.account.AccountDtos.Profile;
import br.com.aura.store.account.AccountDtos.ProfileRequest;
import br.com.aura.store.account.AccountDtos.RegisterRequest;
import br.com.aura.store.security.AuraUser;
import br.com.aura.store.security.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountService {
    private final JdbcTemplate db;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AccountService(JdbcTemplate db, UserRepository users, PasswordEncoder encoder) {
        this.db = db;
        this.users = users;
        this.encoder = encoder;
    }

    /** Todo cadastro público cria um CLIENTE. Administradores não se cadastram pela loja. */
    @Transactional
    public long register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }
        try {
            return users.create(request.name(), request.email(), encoder.encode(request.password()), AuraUser.Role.CUSTOMER);
        } catch (DuplicateKeyException race) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma conta com este e-mail.");
        }
    }

    public Profile profile(long userId) {
        return db.queryForObject("SELECT * FROM users WHERE id = ?", (rs, row) -> new Profile(
                rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getString("role"),
                rs.getString("phone"), rs.getString("address"), rs.getString("city"), rs.getString("zip_code")), userId);
    }

    public Profile updateProfile(long userId, ProfileRequest request) {
        db.update("UPDATE users SET name = ?, phone = ?, address = ?, city = ?, zip_code = ? WHERE id = ?",
                request.name().strip(), blankToNull(request.phone()), blankToNull(request.address()),
                blankToNull(request.city()), blankToNull(request.zipCode()), userId);
        return profile(userId);
    }

    public void changePassword(long userId, PasswordRequest request) {
        String hash = db.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, userId);
        if (!encoder.matches(request.currentPassword(), hash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha atual não confere.");
        }
        db.update("UPDATE users SET password_hash = ? WHERE id = ?", encoder.encode(request.newPassword()), userId);
    }

    /** Somente os pedidos do próprio usuário, do mais recente para o mais antigo. */
    public List<OrderSummary> orders(long userId) {
        Map<UUID, OrderSummary> orders = new LinkedHashMap<>();
        db.query("""
                SELECT o.id, o.created_at, o.status, o.subtotal, o.shipping, o.total,
                       i.product_name, i.quantity, i.unit_price
                FROM orders o JOIN order_items i ON i.order_id = o.id
                WHERE o.user_id = ?
                ORDER BY o.created_at DESC, o.id, i.product_name
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            orders.computeIfAbsent(id, key -> {
                try {
                    return new OrderSummary(key, rs.getObject("created_at", OffsetDateTime.class), rs.getString("status"),
                            rs.getBigDecimal("subtotal"), rs.getBigDecimal("shipping"), rs.getBigDecimal("total"), new ArrayList<>());
                } catch (java.sql.SQLException e) { throw new IllegalStateException(e); }
            }).items().add(new OrderSummary.Item(rs.getString("product_name"), rs.getInt("quantity"), rs.getBigDecimal("unit_price")));
        }, userId);
        return List.copyOf(orders.values());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
