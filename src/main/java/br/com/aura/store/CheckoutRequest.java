package br.com.aura.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CheckoutRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String address,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Pattern(regexp = "[0-9]{5}-?[0-9]{3}", message = "deve ter 8 dígitos") String zipCode,
        @NotNull @Size(min = 1, max = 50) List<@NotNull @Valid Item> items) {
    public record Item(@Positive long productId, @Min(1) @Max(99) int quantity) {}
}
