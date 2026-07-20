package com.lumen.api.controller;

import com.lumen.api.assembler.OrderAssembler;
import com.lumen.api.model.Order;
import com.lumen.api.model.OrderModel;
import com.lumen.api.model.OrderRepository;
import com.lumen.api.model.OrderRequest;
import com.lumen.api.model.OrderStatus;
import com.lumen.api.model.PaymentRequest;
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

    @GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
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

    @PostMapping(produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Place an order for a product",
            description = "Creates a new order for a given productId and quantity. The order is created "
                    + "AWAITING_PAYMENT — it is not a completed purchase until paid via the pay "
                    + "affordance it returns (_templates.default, POST to its x:payment link). This "
                    + "create operation is the same one exposed as the HAL-FORMS affordance on a "
                    + "product's self link — prefer following affordances over constructing requests "
                    + "from documentation alone.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call once you have a productId (from "
                            + "GET /api/products) and know the desired quantity. This starts the "
                            + "transaction; complete it by following the returned pay affordance."),
                    @ExtensionProperty(name = "preconditions", value = "productId must reference an "
                            + "in-stock product; quantity must be a positive integer."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"x:payment\",\"_templates.default\",\"_templates.cancel\",\"self\",\"x:status\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Order two bags of the Yirgacheffe Sunrise and pay for them."),
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
                product.getPrice(), total, Instant.now(), OrderStatus.AWAITING_PAYMENT));

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

    @PostMapping(value = "/{id}/payment", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Pay for an order (completes the transaction)",
            description = "Takes payment for an AWAITING_PAYMENT order and returns it as PAID with a "
                    + "receipt number. Demo payment: method must be 'DEMO_CARD' and any non-blank "
                    + "cardToken (e.g. 'tok_demo') is accepted — no real money moves. Surfaced on "
                    + "unpaid order resources as the HAL-FORMS pay affordance (_templates.default) "
                    + "and the x:payment link; prefer following those.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call after POST /api/orders to "
                            + "complete the purchase. Follow the x:payment link (or _templates.default) "
                            + "on the unpaid order. Body: {\"method\":\"DEMO_CARD\",\"cardToken\":\"tok_demo\"}."),
                    @ExtensionProperty(name = "preconditions", value = "Order status must be "
                            + "AWAITING_PAYMENT. Paying an already-PAID order returns 409; so does a "
                            + "CANCELLED one."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"self\",\"x:status\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Pay for order 1 with the demo card."),
                    @ExtensionProperty(name = "brandingNote", value = "The PAID response embeds a "
                            + "brand-styled receipt in _embedded.presentation (unless you send "
                            + "'Prefer: brand=none'). Rendering that receipt is the natural end of the "
                            + "transaction for the user.")
            })
    )
    public ResponseEntity<OrderModel> payOrder(@PathVariable Long id,
                                                @RequestBody PaymentRequest payment,
                                                @RequestHeader(value = "Prefer", required = false) String prefer,
                                                @RequestHeader(value = "X-Brand-Version", required = false) String brandVersion) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No order with id " + id));
        if (order.getStatus() == OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + id + " is already paid (receipt " + order.getReceiptNumber() + ")");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order " + id + " was cancelled and cannot be paid");
        }
        if (payment == null || !"DEMO_CARD".equalsIgnoreCase(payment.getMethod())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "method must be 'DEMO_CARD' (this demo accepts no other payment methods)");
        }
        if (payment.getCardToken() == null || payment.getCardToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "cardToken is required (any non-blank token, e.g. 'tok_demo')");
        }

        order.markPaid("pay_" + Long.toHexString(System.nanoTime()),
                String.format("LMN-%06d", order.getId()), Instant.now());

        OrderModel model = assembler.toModel(order);
        var decision = brandPreferenceResolver.resolve(prefer, brandVersion);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (decision.includePresentation()) {
            model.setEmbedded(Map.of("presentation", presentationService.forReceipt(order)));
            builder.header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION);
        }
        if (decision.preferenceApplied() != null) {
            builder.header("Preference-Applied", decision.preferenceApplied());
        }
        return builder.body(model);
    }

    @GetMapping(value = "/{id}", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Get order status",
            description = "Returns the current status of an order. While AWAITING_PAYMENT it carries "
                    + "two HAL-FORMS affordances (visible with Accept: application/prs.hal-forms+json): "
                    + "pay (_templates.default, POST) and cancel (_templates.cancel, DELETE), plus an "
                    + "x:payment link. Once PAID it carries the receipt fields and no further actions.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call after placing an order (follow "
                            + "the 'self' or 'x:status' link on the order response) to check its status "
                            + "or re-read its pay/cancel affordances."),
                    @ExtensionProperty(name = "preconditions", value = "A valid order id, obtained from "
                            + "POST /api/orders or GET /api/orders."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"x:payment\",\"_templates.default\",\"_templates.cancel\"]", parseValue = true),
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
            if (order.getStatus() == OrderStatus.PAID) {
                model.setEmbedded(Map.of("presentation", presentationService.forReceipt(order)));
            } else {
                String headline = order.getStatus() == OrderStatus.CANCELLED ? "Order cancelled"
                        : "Order status — awaiting payment";
                model.setEmbedded(Map.of("presentation", presentationService.forOrder(order, headline)));
            }
            builder.header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION);
        }
        if (decision.preferenceApplied() != null) {
            builder.header("Preference-Applied", decision.preferenceApplied());
        }
        return builder.body(model);
    }

    @DeleteMapping(value = "/{id}", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Cancel an order",
            description = "Cancels an unpaid order. Only available while the order's status is "
                    + "AWAITING_PAYMENT — surfaced on the order resource as the HAL-FORMS affordance "
                    + "keyed '_templates.cancel' with method DELETE, which disappears once the order "
                    + "is paid or cancelled. Paid orders cannot be cancelled in this demo (no refunds).",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call to abandon an unpaid order the "
                            + "agent (or user) no longer wants. Prefer following the order resource's "
                            + "own DELETE affordance (_templates.cancel) rather than calling this "
                            + "directly from documentation."),
                    @ExtensionProperty(name = "preconditions", value = "A valid order id whose status is "
                            + "still AWAITING_PAYMENT. PAID and CANCELLED orders return 409."),
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
        if (order.getStatus() == OrderStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Order " + id + " is already paid — refunds aren't supported in this demo");
        }
        order.setStatus(OrderStatus.CANCELLED);

        OrderModel model = assembler.toModel(order);
        model.setEmbedded(Map.of("presentation", presentationService.forOrder(order, "Order cancelled")));
        return ResponseEntity.ok()
                .header("Brand-Version", com.lumen.api.support.BrandTokens.VERSION)
                .body(model);
    }
}
