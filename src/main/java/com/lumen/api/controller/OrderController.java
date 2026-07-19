package com.lumen.api.controller;

import com.lumen.api.assembler.OrderAssembler;
import com.lumen.api.model.Order;
import com.lumen.api.model.OrderModel;
import com.lumen.api.model.OrderRepository;
import com.lumen.api.model.OrderRequest;
import com.lumen.api.model.OrderStatus;
import com.lumen.api.model.Product;
import com.lumen.api.model.ProductRepository;
import com.lumen.api.support.ApiLinks;
import com.lumen.api.support.BrandPreferenceResolver;
import com.lumen.api.support.PresentationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.mediatype.Affordances;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderAssembler assembler;
    private final PresentationService presentationService;
    private final BrandPreferenceResolver brandPreferenceResolver;

    public OrderController(OrderRepository orderRepository, ProductRepository productRepository,
                            OrderAssembler assembler, PresentationService presentationService,
                            BrandPreferenceResolver brandPreferenceResolver) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.assembler = assembler;
        this.presentationService = presentationService;
        this.brandPreferenceResolver = brandPreferenceResolver;
    }

    @GetMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "List orders and discover how to place one",
            description = "Returns all orders placed so far. The self link carries a HAL-FORMS "
                    + "'createOrder' affordance describing exactly how to POST a new order.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call to review existing orders, or to "
                            + "read the create-order affordance (the single entry under _templates, "
                            + "keyed 'default') if you haven't already seen the equivalent affordance on "
                            + "a product."),
                    @ExtensionProperty(name = "preconditions", value = "None."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"_templates.default\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "What orders have been placed so far?")
            })
    )
    public CollectionModel<OrderModel> getAllOrders() {
        List<OrderModel> models = orderRepository.findAll().stream().map(assembler::toModel).toList();
        CollectionModel<OrderModel> collection = CollectionModel.of(models);

        Link self = linkTo(methodOn(OrderController.class).getAllOrders()).withSelfRel();
        Link createTarget = linkTo(methodOn(OrderController.class).createOrder(null, null, null)).withRel("createOrder");
        Link selfWithAffordance = Affordances.of(self)
                .afford(HttpMethod.POST)
                .withTarget(createTarget)
                .withInput(OrderRequest.class)
                .withInputMediaType(MediaType.APPLICATION_JSON)
                .withName("createOrder")
                .toLink();
        collection.add(selfWithAffordance);
        collection.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        return collection;
    }

    @PostMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Place an order for a product",
            description = "Creates a new order for a given productId and quantity. This is the same "
                    + "operation exposed as the HAL-FORMS affordance on a product's self link and on the "
                    + "orders collection's self link (both surface it as the single entry under "
                    + "_templates, keyed 'default') — prefer following those affordances over "
                    + "constructing this request from documentation alone.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call once you have a productId (from "
                            + "GET /api/products) and know the desired quantity. This is the actual "
                            + "'ordering a coffee' action."),
                    @ExtensionProperty(name = "preconditions", value = "productId must reference an "
                            + "in-stock product; quantity must be a positive integer."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"self\",\"x:status\",\"_templates.default\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Order two bags of the Yirgacheffe Sunrise."),
                    @ExtensionProperty(name = "brandingNote", value = "If you have already retrieved branding "
                            + "(via x-branding or GET /brand), send 'Prefer: brand=none' and style the "
                            + "confirmation yourself; otherwise the response's _embedded.presentation "
                            + "fragment is ready to render as-is.")
            })
    )
    public ResponseEntity<OrderModel> createOrder(@RequestBody OrderRequest request,
                                                   @RequestHeader(value = "Prefer", required = false) String prefer,
                                                   @RequestHeader(value = "X-Brand-Version", required = false) String brandVersion) {
        if (request == null || request.getProductId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productId is required");
        }
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "quantity must be a positive integer");
        }
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No product with id " + request.getProductId()));
        if (!product.isInStock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product '" + product.getName() + "' is out of stock");
        }

        BigDecimal total = product.getPrice().multiply(BigDecimal.valueOf(request.getQuantity()));
        Order order = orderRepository.save(new Order(null, product.getId(), product.getName(), request.getQuantity(),
                product.getPrice(), total, Instant.now(), OrderStatus.PLACED));

        OrderModel model = assembler.toModel(order);
        var decision = brandPreferenceResolver.resolve(prefer, brandVersion);

        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.LOCATION, model.getRequiredLink("self").getHref());

        if (decision.includePresentation()) {
            model.setEmbedded(Map.of("presentation", presentationService.forOrder(order, "Order confirmed!")));
            builder.header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION);
        }
        if (decision.preferenceApplied() != null) {
            builder.header("Preference-Applied", decision.preferenceApplied());
        }
        return builder.body(model);
    }

    @GetMapping(value = "/{id}", produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Get order status",
            description = "Returns the current status of an order. While the order can still be "
                    + "cancelled, the response carries a HAL-FORMS cancel affordance — the entry under "
                    + "_templates keyed 'default', with method DELETE (visible with Accept: "
                    + "application/prs.hal-forms+json).",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call after placing an order (follow "
                            + "the 'self' or 'x:status' link on the order response) to check its status."),
                    @ExtensionProperty(name = "preconditions", value = "A valid order id, obtained from "
                            + "POST /api/orders or GET /api/orders."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"_templates.default\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "What's the status of order 3?")
            })
    )
    public ResponseEntity<OrderModel> getOrder(@PathVariable Long id,
                                                @RequestHeader(value = "Prefer", required = false) String prefer,
                                                @RequestHeader(value = "X-Brand-Version", required = false) String brandVersion) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order with id " + id));

        OrderModel model = assembler.toModel(order);
        var decision = brandPreferenceResolver.resolve(prefer, brandVersion);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (decision.includePresentation()) {
            String headline = order.getStatus() == OrderStatus.CANCELLED ? "Order cancelled" : "Order status";
            model.setEmbedded(Map.of("presentation", presentationService.forOrder(order, headline)));
            builder.header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION);
        }
        if (decision.preferenceApplied() != null) {
            builder.header("Preference-Applied", decision.preferenceApplied());
        }
        return builder.body(model);
    }

    @DeleteMapping(value = "/{id}", produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Cancel an order",
            description = "Cancels a placed order. Only available while the order's status is PLACED — "
                    + "surfaced on the order resource as the HAL-FORMS affordance keyed "
                    + "'_templates.default' with method DELETE, which disappears once the order is "
                    + "already cancelled.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call to cancel an order the agent (or "
                            + "user) no longer wants. Prefer following the order resource's own DELETE "
                            + "affordance (_templates.default) rather than calling this directly from "
                            + "documentation."),
                    @ExtensionProperty(name = "preconditions", value = "A valid order id whose status is "
                            + "still PLACED."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"self\",\"x:status\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Cancel order 3.")
            })
    )
    public ResponseEntity<OrderModel> cancelOrder(@PathVariable Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order with id " + id));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + id + " is already cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);

        OrderModel model = assembler.toModel(order);
        model.setEmbedded(Map.of("presentation", presentationService.forOrder(order, "Order cancelled")));
        return ResponseEntity.ok()
                .header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION)
                .body(model);
    }
}
