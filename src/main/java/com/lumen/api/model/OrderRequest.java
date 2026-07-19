package com.lumen.api.model;

/**
 * Request body for {@code POST /api/orders}. Also drives the shape of the placeOrder
 * HAL-FORMS affordance generated on product responses.
 *
 * <p>Deliberately a plain mutable class (not a record): Spring HATEOAS's HAL-FORMS
 * property introspection uses standard JavaBean getter/setter detection, and record
 * accessors (e.g. {@code productId()}) aren't recognized as writable, which marks the
 * generated form fields {@code readOnly: true}. A conventional bean keeps the
 * affordance's properties genuinely fillable.
 */
public class OrderRequest {

    private Long productId;
    private Integer quantity;

    public OrderRequest() {
    }

    public OrderRequest(Long productId, Integer quantity) {
        this.productId = productId;
        this.quantity = quantity;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
