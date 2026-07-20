package com.lumen.api.assembler;

import com.lumen.api.controller.OrderController;
import com.lumen.api.model.Order;
import com.lumen.api.model.OrderModel;
import com.lumen.api.model.OrderStatus;
import com.lumen.api.model.PaymentRequest;
import com.lumen.api.support.ApiLinks;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds {@link OrderModel} HAL representations: self, {@code x:status}, describedby,
 * plus status-dependent HAL-FORMS affordances on the self link. While the order is
 * AWAITING_PAYMENT it carries two: pay (keyed {@code _templates.default} on the wire,
 * being first) and cancel (keyed {@code _templates.cancel}). Paid or cancelled orders
 * carry none — the resource itself tells the agent no further actions exist.
 */
@Component
public class OrderAssembler implements RepresentationModelAssembler<Order, OrderModel> {

    @Override
    public OrderModel toModel(Order order) {
        OrderModel model = new OrderModel(order.getId(), order.getProductId(), order.getProductName(),
                order.getQuantity(), order.getUnitPrice(), order.getTotalPrice(), order.getCreatedAt(),
                order.getStatus(), order.getReceiptNumber(), order.getPaidAt());

        Link selfBase = linkTo(methodOn(OrderController.class).getOrder(order.getId(), null, null)).withSelfRel();

        Link selfFinal = selfBase;
        Link paymentLink = null;
        if (order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
            Link payTarget = linkTo(methodOn(OrderController.class)
                    .payOrder(order.getId(), null, null, null)).withRel("pay");
            selfFinal = Affordances.of(selfBase)
                    .afford(HttpMethod.POST)
                    .withTarget(payTarget)
                    .withInput(PaymentRequest.class)
                    .withInputMediaType(MediaType.APPLICATION_JSON)
                    .withName("pay")
                    .andAfford(HttpMethod.DELETE)
                    .withTarget(linkTo(methodOn(OrderController.class).cancelOrder(order.getId())).withRel("cancel"))
                    .withName("cancel")
                    .toLink();
            paymentLink = Link.of(payTarget.getHref(), "x:payment");
        }

        model.add(selfFinal);
        if (paymentLink != null) {
            model.add(paymentLink);
        }
        model.add(Link.of(selfBase.getHref(), "x:status"));
        model.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        return model;
    }
}
