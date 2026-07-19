package com.lumen.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import com.lumen.api.model.RootModel;
import com.lumen.api.support.ApiLinks;
import com.lumen.api.support.BrandTokens;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

/**
 * The one URL an agent ever needs. GET /api is the HAL entry point; GET / is a
 * deliberately minimal human page making the thesis literal.
 */
@RestController
public class RootController {

    @GetMapping(value = "/api", produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Agent entry point",
            description = "The root of this business's entire API surface. Returns a welcome message "
                    + "plus links to everything else: the OpenAPI document, branding, the product "
                    + "catalog, orders, and capability search.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Always call this first. Never "
                            + "construct any other URL by hand — follow _links from here and from every "
                            + "subsequent response."),
                    @ExtensionProperty(name = "preconditions", value = "None."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"describedby\",\"x:brand\",\"x:products\",\"x:orders\",\"x:search\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Starting only from this URL, find out what this business is and how to order a coffee.")
            })
    )
    public RootModel root() {
        RootModel model = new RootModel(
                "Welcome, agent. This business's entire presence is this API — there is no separate "
                        + "website to browse. Follow the links below; never construct URLs by hand. "
                        + "Write actions (place order, cancel) are exposed as HAL-FORMS _templates on "
                        + "the resources themselves — request with 'Accept: application/prs.hal-forms+json' "
                        + "to see them.",
                BrandTokens.NAME
        );
        model.add(linkTo(methodOn(RootController.class).root()).withSelfRel());
        model.add(ApiLinks.absolute("/v3/api-docs", "describedby"));
        model.add(ApiLinks.absolute("/brand", "x:brand"));
        model.add(linkTo(methodOn(ProductController.class).getAllProducts()).withRel("x:products"));
        model.add(linkTo(methodOn(OrderController.class).getAllOrders()).withRel("x:orders"));
        model.add(ApiLinks.absolute("/api/search{?q}", "x:search"));
        return model;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "Minimal human landing page",
            description = "A deliberately minimal HTML page for human visitors: this business has no "
                    + "website to browse, only an API. Points humans (and any agent that lands here by "
                    + "mistake) at GET /api."
    )
    public ResponseEntity<String> humanHome() {
        String html = """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <title>Lumen Coffee Roasters</title>
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <style>
                    body { font-family: 'Helvetica Neue', Arial, sans-serif; background: #1C1917; color: #FEF3C7;
                           display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; }
                    main { text-align: center; max-width: 560px; padding: 2rem; }
                    h1 { font-family: Georgia, serif; color: #D97706; font-size: 2rem; margin-bottom: .5rem; }
                    p { line-height: 1.6; }
                    code { background: #292524; color: #FEF3C7; padding: .15rem .4rem; border-radius: 4px; }
                    a { color: #D97706; }
                  </style>
                </head>
                <body>
                  <main>
                    <h1>Lumen Coffee Roasters</h1>
                    <p>This business is agent-first. There is no website to browse — only an API.</p>
                    <p>Point your agent at <a href="/api"><code>/api</code></a>.</p>
                  </main>
                </body>
                </html>
                """;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
