package com.lumen.api.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * An order. Mutable only in its status field; everything else is fixed at creation.
 * In-memory only — no database.
 */
public class Order {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;
    private final Instant createdAt;
    private OrderStatus status;

    public Order(Long id, Long productId, String productName, int quantity,
                 BigDecimal unitPrice, BigDecimal totalPrice, Instant createdAt, OrderStatus status) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
