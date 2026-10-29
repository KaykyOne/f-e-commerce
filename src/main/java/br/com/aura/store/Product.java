package br.com.aura.store;

import java.math.BigDecimal;

public record Product(long id, String name, String category, String description,
                      BigDecimal price, BigDecimal originalPrice, String image,
                      String color, String badge, int stock) {}
