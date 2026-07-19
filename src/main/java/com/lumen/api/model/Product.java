package com.lumen.api.model;

import java.math.BigDecimal;

/**
 * A coffee product. Immutable, in-memory only — no database.
 */
public class Product {

    private final Long id;
    private final String name;
    private final String origin;
    private final String roastLevel;
    private final String description;
    private final BigDecimal price;
    private final boolean inStock;

    public Product(Long id, String name, String origin, String roastLevel, String description,
                    BigDecimal price, boolean inStock) {
        this.id = id;
        this.name = name;
        this.origin = origin;
        this.roastLevel = roastLevel;
        this.description = description;
        this.price = price;
        this.inStock = inStock;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrigin() {
        return origin;
    }

    public String getRoastLevel() {
        return roastLevel;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isInStock() {
        return inStock;
    }
}
