package br.com.aura.store.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cria o primeiro administrador na inicialização, se ainda não existir nenhum.
 * Credenciais vêm de APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD (os padrões servem só para desenvolvimento).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public AdminBootstrap(UserRepository users, PasswordEncoder encoder,
                          @Value("${app.admin.email}") String email,
                          @Value("${app.admin.password}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.adminExists()) return;
        if (password.length() < 8) throw new IllegalStateException("app.admin.password precisa ter pelo menos 8 caracteres.");
        users.create("Administrador", email, encoder.encode(password), AuraUser.Role.ADMIN);
        log.info("Administrador inicial criado: {}", UserRepository.normalizeEmail(email));
    }
}
