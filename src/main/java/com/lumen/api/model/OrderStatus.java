package com.lumen.api.model;

/**
 * Order lifecycle: created as AWAITING_PAYMENT, becomes PAID via
 * {@code POST /api/orders/{id}/payment}, or CANCELLED while still unpaid.
 */
public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    CANCELLED
}
