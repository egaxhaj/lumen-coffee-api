package com.lumen.api.model;

/**
 * Request body for {@code POST /api/orders/{id}/payment}. Drives the HAL-FORMS pay
 * affordance on unpaid orders. Plain bean, not a record, for the same HAL-FORMS
 * introspection reason as {@link OrderRequest}.
 *
 * <p>Demo payment only: {@code method} must be {@code DEMO_CARD} and any non-blank
 * {@code cardToken} (e.g. {@code tok_demo}) is accepted. No real money moves.
 */
public class PaymentRequest {

    private String method;
    private String cardToken;

    public PaymentRequest() {
    }

    public PaymentRequest(String method, String cardToken) {
        this.method = method;
        this.cardToken = cardToken;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }
}
