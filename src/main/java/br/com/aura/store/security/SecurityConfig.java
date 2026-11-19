package br.com.aura.store.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Perfis de acesso:
 * <ul>
 *   <li><b>Visitante</b>: loja, catálogo, cadastro e login.</li>
 *   <li><b>Cliente</b> (ROLE_CUSTOMER): tudo do visitante + comprar, perfil e "Meus pedidos".</li>
 *   <li><b>Administrador</b> (ROLE_ADMIN): painel com produtos, pedidos e resumo da loja.</li>
 * </ul>
 * Páginas protegidas redirecionam o visitante para /entrar?next=...; a API responde 401/403 em JSON.
 */
@Configuration
public class SecurityConfig {

    private static final RequestMatcher API = request -> request.getRequestURI().startsWith("/api/");

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt
    }

    @Bean
    AuthenticationManager authenticationManager(UserRepository users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        // O JavaScript lê o cookie XSRF-TOKEN e devolve o valor no cabeçalho X-XSRF-TOKEN.
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CsrfTokenRepository csrf, SecurityContextRepository contexts) throws Exception {
        http
            .securityContext(context -> context.securityContextRepository(contexts))
            .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/error", "/error/**", "/actuator/health").permitAll()
                .requestMatchers("/admin", "/admin.html", "/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/conta", "/conta.html", "/api/account/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/products").permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/**").denyAll()
                .anyRequest().permitAll()) // páginas públicas, arquivos estáticos e rotas inexistentes (404)
            .exceptionHandling(e -> e.authenticationEntryPoint(apiOrLoginPage()))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /**
     * Sem login: a API responde 401 (o JavaScript decide o que fazer); páginas protegidas
     * redirecionam para o login e voltam para onde o visitante estava depois de entrar.
     */
    private static AuthenticationEntryPoint apiOrLoginPage() {
        AuthenticationEntryPoint api = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
        return (request, response, exception) -> {
            if (API.matches(request)) {
                api.commence(request, response, exception);
                return;
            }
            String target = request.getRequestURI() + (request.getQueryString() == null ? "" : "?" + request.getQueryString());
            response.sendRedirect("/entrar?next=" + URLEncoder.encode(target, StandardCharsets.UTF_8));
        };
    }

    /** Garante que o cookie XSRF-TOKEN exista desde a primeira página carregada. */
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(request, response);
        }
    }
}
