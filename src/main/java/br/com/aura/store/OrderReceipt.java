package br.com.aura.store;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderReceipt(UUID id, String status, BigDecimal subtotal, BigDecimal shipping,
                           BigDecimal total, List<Line> items) {
    public record Line(long productId, String name, int quantity, BigDecimal unitPrice) {}
}
