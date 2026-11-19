package br.com.aura.store.admin;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class AdminDtos {
    private AdminDtos() {}

    public record ProductRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "Áudio|Wearables|Setup|Lifestyle|Acessórios", message = "deve ser uma categoria da loja") String category,
            @NotBlank @Size(max = 500) String description,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal originalPrice,
            @NotBlank @Pattern(regexp = "/images/[a-z0-9-]+\\.svg", message = "deve ser uma imagem da loja (/images/....svg)") String image,
            @NotBlank @Size(max = 40) String color,
            @Size(max = 40) String badge,
            @NotNull @Min(0) @Max(100000) Integer stock,
            Boolean active) {}

    public record AdminProduct(long id, String name, String category, String description, BigDecimal price,
                               BigDecimal originalPrice, String image, String color, String badge, int stock, boolean active) {}

    public record AdminOrder(UUID id, OffsetDateTime createdAt, String customerName, String email,
                             String status, BigDecimal total, int units) {}

    public record Summary(int activeProducts, int lowStock, int orders, BigDecimal revenue, int customers) {}
}
