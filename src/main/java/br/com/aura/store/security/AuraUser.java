package br.com.aura.store.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Usuário autenticado na sessão. O papel vira a autoridade ROLE_CUSTOMER ou ROLE_ADMIN. */
public record AuraUser(long id, String name, String email, String passwordHash, Role role) implements UserDetails {

    public enum Role { CUSTOMER, ADMIN }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }

    /** Nunca expõe o hash da senha em logs ou mensagens de erro. */
    @Override public String toString() { return "AuraUser[id=" + id + ", email=" + email + ", role=" + role + "]"; }
}
