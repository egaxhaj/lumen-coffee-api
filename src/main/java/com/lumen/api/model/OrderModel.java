package com.lumen.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * HAL representation of an {@link Order}. Carries an optional {@code _embedded.presentation}
 * block (brand-styled confirmation/status card) depending on the caller's {@code Prefer} header.
 */
@Relation(collectionRelation = "orders", itemRelation = "order")
public class OrderModel extends RepresentationModel<OrderModel> {

    private final Long id;
    private final Long productId;
    private final String productName;
    private final int quantity;
    private final BigDecimal unitPrice;
    private final BigDecimal totalPrice;
    private final Instant createdAt;
    private final OrderStatus status;

    @JsonProperty("_embedded")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> embedded;

    public OrderModel(Long id, Long productId, String productName, int quantity, BigDecimal unitPrice,
                       BigDecimal totalPrice, Instant createdAt, OrderStatus status) {
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

    public void setEmbedded(Map<String, Object> embedded) {
        this.embedded = embedded;
    }
}
