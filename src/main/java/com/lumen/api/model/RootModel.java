package com.lumen.api.model;

import org.springframework.hateoas.RepresentationModel;

/**
 * The one representation an agent needs to bootstrap from: a welcome message plus
 * every top-level capability as a link. Served at {@code GET /api}.
 */
public class RootModel extends RepresentationModel<RootModel> {

    private final String message;
    private final String business;

    public RootModel(String message, String business) {
        this.message = message;
        this.business = business;
    }

    public String getMessage() {
        return message;
    }

    public String getBusiness() {
        return business;
    }
}
