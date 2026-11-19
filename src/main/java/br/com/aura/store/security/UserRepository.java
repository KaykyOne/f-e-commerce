package br.com.aura.store.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;

@Repository
public class UserRepository implements UserDetailsService {
    private static final RowMapper<AuraUser> USER = (rs, row) -> new AuraUser(
            rs.getLong("id"), rs.getString("name"), rs.getString("email"),
            rs.getString("password_hash"), AuraUser.Role.valueOf(rs.getString("role")));

    private final JdbcTemplate db;

    public UserRepository(JdbcTemplate db) { this.db = db; }

    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }

    public Optional<AuraUser> findByEmail(String email) {
        return db.query("SELECT * FROM users WHERE email = ?", USER, normalizeEmail(email)).stream().findFirst();
    }

    public boolean existsByEmail(String email) {
        return Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE email = ?", Boolean.class, normalizeEmail(email)));
    }

    public boolean adminExists() {
        return Boolean.TRUE.equals(db.queryForObject("SELECT COUNT(*) > 0 FROM users WHERE role = 'ADMIN'", Boolean.class));
    }

    public long create(String name, String email, String passwordHash, AuraUser.Role role) {
        long id = db.queryForObject("SELECT nextval('users_seq')", Long.class);
        db.update("INSERT INTO users (id, name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                id, name.strip(), normalizeEmail(email), passwordHash, role.name());
        return id;
    }

    @Override
    public AuraUser loadUserByUsername(String email) {
        return findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
