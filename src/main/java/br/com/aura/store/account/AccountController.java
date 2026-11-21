package br.com.aura.store.account;

import br.com.aura.store.account.AccountDtos.LoginRequest;
import br.com.aura.store.account.AccountDtos.OrderSummary;
import br.com.aura.store.account.AccountDtos.PasswordRequest;
import br.com.aura.store.account.AccountDtos.Profile;
import br.com.aura.store.account.AccountDtos.ProfileRequest;
import br.com.aura.store.account.AccountDtos.RegisterRequest;
import br.com.aura.store.account.AccountDtos.Session;
import br.com.aura.store.security.AuraUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    private final AuthenticationManager authentication;
    private final SecurityContextRepository contexts;
    private final CsrfTokenRepository csrf;

    public AccountController(AccountService accounts, AuthenticationManager authentication,
                             SecurityContextRepository contexts, CsrfTokenRepository csrf) {
        this.accounts = accounts;
        this.authentication = authentication;
        this.contexts = contexts;
        this.csrf = csrf;
    }

    @GetMapping("/auth/me")
    public Session me(@AuthenticationPrincipal AuraUser user) {
        return user == null ? Session.anonymous() : new Session(true, accounts.profile(user.id()));
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Session register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request, HttpServletResponse response) {
        accounts.register(body);
        return signIn(body.email(), body.password(), request, response);
    }

    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        return signIn(body.email(), body.password(), request, response);
    }

    @PutMapping("/account")
    public Profile updateProfile(@AuthenticationPrincipal AuraUser user, @Valid @RequestBody ProfileRequest body) {
        return accounts.updateProfile(user.id(), body);
    }

    @PutMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal AuraUser user, @Valid @RequestBody PasswordRequest body) {
        accounts.changePassword(user.id(), body);
    }

    @GetMapping("/account/orders")
    public List<OrderSummary> myOrders(@AuthenticationPrincipal AuraUser user) {
        return accounts.orders(user.id());
    }

    private Session signIn(String email, String password, HttpServletRequest request, HttpServletResponse response) {
        Authentication result;
        try {
            result = authentication.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email.strip(), password));
        } catch (AuthenticationException wrong) {
            // Mesma mensagem para e-mail inexistente e senha errada: não revela quais e-mails têm conta.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha incorretos.");
        }
        // Nova sessão e novo token CSRF após o login (evita fixação de sessão).
        if (request.getSession(false) != null) request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(result);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        csrf.saveToken(csrf.generateToken(request), request, response);
        return new Session(true, accounts.profile(((AuraUser) result.getPrincipal()).id()));
    }
}
