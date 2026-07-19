package com.lumen.api.assembler;

import com.lumen.api.controller.OrderController;
import com.lumen.api.model.Order;
import com.lumen.api.model.OrderModel;
import com.lumen.api.model.OrderStatus;
import com.lumen.api.support.ApiLinks;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * Builds {@link OrderModel} HAL representations: self, {@code x:status}, describedby,
 * and — while the order is still cancellable — a HAL-FORMS DELETE affordance (keyed
 * {@code _templates.default} on the wire, since it's the resource's sole affordance).
 */
@Component
public class OrderAssembler implements RepresentationModelAssembler<Order, OrderModel> {

    @Override
    public OrderModel toModel(Order order) {
        OrderModel model = new OrderModel(order.getId(), order.getProductId(), order.getProductName(),
                order.getQuantity(), order.getUnitPrice(), order.getTotalPrice(), order.getCreatedAt(),
                order.getStatus());

        Link selfBase = linkTo(methodOn(OrderController.class).getOrder(order.getId(), null, null)).withSelfRel();

        Link selfFinal = order.getStatus() == OrderStatus.PLACED
                ? Affordances.of(selfBase)
                        .afford(HttpMethod.DELETE)
                        .withTarget(linkTo(methodOn(OrderController.class).cancelOrder(order.getId())).withRel("cancel"))
                        .withName("cancel")
                        .toLink()
                : selfBase;

        model.add(selfFinal);
        model.add(Link.of(selfBase.getHref(), "x:status"));
        model.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        return model;
    }
}
