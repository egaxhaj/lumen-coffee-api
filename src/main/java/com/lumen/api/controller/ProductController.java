package com.lumen.api.controller;

import com.lumen.api.assembler.ProductAssembler;
import com.lumen.api.model.Product;
import com.lumen.api.model.ProductModel;
import com.lumen.api.model.ProductRepository;
import com.lumen.api.support.ApiLinks;
import com.lumen.api.support.BrandPreferenceResolver;
import com.lumen.api.support.BrandTokens;
import com.lumen.api.support.PresentationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository repository;
    private final ProductAssembler assembler;
    private final PresentationService presentationService;
    private final BrandPreferenceResolver brandPreferenceResolver;

    public ProductController(ProductRepository repository, ProductAssembler assembler,
                              PresentationService presentationService, BrandPreferenceResolver brandPreferenceResolver) {
        this.repository = repository;
        this.assembler = assembler;
        this.presentationService = presentationService;
        this.brandPreferenceResolver = brandPreferenceResolver;
    }

    @GetMapping(produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "List the coffee catalog",
            description = "Returns every product Lumen Coffee Roasters sells, each with a self link "
                    + "to fetch full details and place an order.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call first when the task involves "
                            + "browsing or ordering coffee and you don't yet know a productId."),
                    @ExtensionProperty(name = "preconditions", value = "None."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"self\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "What coffees do you sell?")
            })
    )
    public CollectionModel<ProductModel> getAllProducts() {
        List<ProductModel> models = repository.findAll().stream().map(assembler::toModel).toList();
        CollectionModel<ProductModel> collection = CollectionModel.of(models);
        collection.add(linkTo(methodOn(ProductController.class).getAllProducts()).withSelfRel());
        collection.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        return collection;
    }

    @GetMapping(value = "/{id}", produces = { MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Get a single coffee product",
            description = "Returns full details for one product. Its self link carries a HAL-FORMS "
                    + "affordance for placing an order (the single entry under _templates, keyed "
                    + "'default') — the agent learns how to order directly from this response, no "
                    + "separate docs read required.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call after GET /api/products to "
                            + "inspect one product before ordering, or when a productId is already known."),
                    @ExtensionProperty(name = "preconditions", value = "A valid productId, typically "
                            + "obtained from GET /api/products."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"_templates.default\",\"x:products\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Show me the Yirgacheffe Sunrise and order two."),
                    @ExtensionProperty(name = "brandingNote", value = "If you have already retrieved "
                            + "branding (via x-branding or GET /brand), send 'Prefer: brand=none' and "
                            + "style the output yourself; otherwise render the response's "
                            + "_embedded.presentation fragment as-is.")
            })
    )
    public ResponseEntity<ProductModel> getProduct(@PathVariable Long id,
                                                     @RequestHeader(value = "Prefer", required = false) String prefer,
                                                     @RequestHeader(value = "X-Brand-Version", required = false) String brandVersion) {
        Product product = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No product with id " + id));

        ProductModel model = assembler.toModel(product);
        var decision = brandPreferenceResolver.resolve(prefer, brandVersion);

        ResponseEntity.BodyBuilder builder = ResponseEntity.ok();
        if (decision.includePresentation()) {
            model.setEmbedded(Map.of("presentation", presentationService.forProduct(product)));
            builder.header("Brand-Version", BrandTokens.VERSION);
        }
        if (decision.preferenceApplied() != null) {
            builder.header("Preference-Applied", decision.preferenceApplied());
        }
        return builder.body(model);
    }
}
