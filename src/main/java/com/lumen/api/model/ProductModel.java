package com.lumen.api.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.hateoas.RepresentationModel;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * HAL representation of a {@link Product}. Carries an optional {@code _embedded.presentation}
 * block (brand-styled fragment) depending on the caller's {@code Prefer} header.
 */
@Relation(collectionRelation = "products", itemRelation = "product")
public class ProductModel extends RepresentationModel<ProductModel> {

    private final Long id;
    private final String name;
    private final String origin;
    private final String roastLevel;
    private final String description;
    private final BigDecimal price;
    private final boolean inStock;

    @JsonProperty("_embedded")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Object> embedded;

    public ProductModel(Long id, String name, String origin, String roastLevel, String description,
                         BigDecimal price, boolean inStock) {
        this.id = id;
        this.name = name;
        this.origin = origin;
        this.roastLevel = roastLevel;
        this.description = description;
        this.price = price;
        this.inStock = inStock;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOrigin() {
        return origin;
    }

    public String getRoastLevel() {
        return roastLevel;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isInStock() {
        return inStock;
    }

    public void setEmbedded(Map<String, Object> embedded) {
        this.embedded = embedded;
    }
}
