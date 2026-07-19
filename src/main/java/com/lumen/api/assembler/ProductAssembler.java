package com.lumen.api.assembler;

import com.lumen.api.controller.OrderController;
import com.lumen.api.controller.ProductController;
import com.lumen.api.model.OrderRequest;
import com.lumen.api.model.Product;
import com.lumen.api.model.ProductModel;
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
 * Builds {@link ProductModel} HAL representations: self/collection/describedby links,
 * plus a HAL-FORMS {@code placeOrder} affordance so an agent can order directly from
 * a product's own response — no separate docs read required.
 */
@Component
public class ProductAssembler implements RepresentationModelAssembler<Product, ProductModel> {

    @Override
    public ProductModel toModel(Product product) {
        ProductModel model = new ProductModel(product.getId(), product.getName(), product.getOrigin(),
                product.getRoastLevel(), product.getDescription(), product.getPrice(), product.isInStock());

        Link selfLink = linkTo(methodOn(ProductController.class).getProduct(product.getId(), null, null)).withSelfRel();
        Link orderTarget = linkTo(methodOn(OrderController.class).createOrder(null, null, null)).withRel("placeOrder");
        Link selfWithAffordance = Affordances.of(selfLink)
                .afford(HttpMethod.POST)
                .withTarget(orderTarget)
                .withInput(OrderRequest.class)
                .withInputMediaType(MediaType.APPLICATION_JSON)
                .withName("placeOrder")
                .toLink();

        model.add(selfWithAffordance);
        model.add(linkTo(methodOn(ProductController.class).getAllProducts()).withRel("x:products"));
        model.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        return model;
    }
}
