package br.com.aura.store.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Formatos de entrada e saída da conta do usuário. */
public final class AccountDtos {
    private AccountDtos() {}

    /** Senha: 8 a 72 caracteres (limite do bcrypt), com pelo menos uma letra e um número. */
    static final String PASSWORD_RULE = "^(?=.*[A-Za-zÀ-ÿ])(?=.*\\d).{8,72}$";
    static final String PASSWORD_MESSAGE = "deve ter de 8 a 72 caracteres, com letras e números";

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String password) {}

    public record LoginRequest(@NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

    public record ProfileRequest(
            @NotBlank @Size(max = 100) String name,
            @Pattern(regexp = "^$|^[0-9 ()+-]{8,20}$", message = "telefone inválido") String phone,
            @Size(max = 200) String address,
            @Size(max = 100) String city,
            @Pattern(regexp = "^$|^[0-9]{5}-?[0-9]{3}$", message = "deve ter 8 dígitos") String zipCode) {}

    public record PasswordRequest(
            @NotBlank @Size(max = 72) String currentPassword,
            @NotBlank @Pattern(regexp = PASSWORD_RULE, message = PASSWORD_MESSAGE) String newPassword) {}

    /** Sessão vista pelo frontend. Visitante recebe authenticated=false (sem erro 401 no console). */
    public record Session(boolean authenticated, Profile user) {
        static Session anonymous() { return new Session(false, null); }
    }

    public record Profile(long id, String name, String email, String role, String phone,
                          String address, String city, String zipCode) {}

    public record OrderSummary(UUID id, OffsetDateTime createdAt, String status, BigDecimal subtotal,
                               BigDecimal shipping, BigDecimal total, List<Item> items) {
        public record Item(String name, int quantity, BigDecimal unitPrice) {}
    }
}
