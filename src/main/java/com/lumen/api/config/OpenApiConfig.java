package com.lumen.api.config;

import com.lumen.api.support.BrandTokens;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dynamic OpenAPI setup (springdoc regenerates /v3/api-docs from the controllers on
 * every request — nothing here is hand-maintained):
 *
 * <ul>
 *   <li>An agent-facing {@code info.description} explaining how to use this API.</li>
 *   <li>An {@link OpenApiCustomizer} that stamps the root-level {@code x-branding}
 *       extension onto the generated document — brand representation #1 (representation
 *       #2 is the linked {@code /brand} resource).</li>
 * </ul>
 *
 * Per-operation {@code x-llm} guidance lives on the controller methods themselves via
 * {@code @Operation(extensions = ...)}, so it stays in sync with the code automatically.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI lumenOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Lumen Coffee Roasters API")
                .version("1.0.0")
                .description("""
                        You are an AI agent. This business's entire presence is this API — there is no \
                        separate website to browse. Start at GET /api and follow the _links in every \
                        response; never construct URLs by hand.

                        Branding is available two ways: the x-branding block on this document (see \
                        below), and the linked x:brand resource, content-negotiated via Accept: \
                        text/html (full marketing page), text/markdown, or application/json (structured \
                        tokens).

                        Every operation below carries an x-llm extension — whenToUse, preconditions, \
                        followUpRels, examplePrompt, and sometimes brandingNote. Read it before calling \
                        an endpoint you have not used yet.

                        To order a coffee: GET /api/products, pick one, then use the placeOrder \
                        affordance in that product's response (_templates.default, HAL-FORMS) to POST \
                        /api/orders. If you already have branding, send Prefer: brand=none on product and \
                        order requests to skip the embedded presentation fragment.

                        To find a capability by description rather than by reading this whole document, \
                        use GET /api/search?q=.
                        """)
        );
    }

    @Bean
    public OpenApiCustomizer brandingCustomizer() {
        return openApi -> {
            Map<String, Object> extensions = openApi.getExtensions() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(openApi.getExtensions());
            extensions.put("x-branding", BrandTokens.asOpenApiExtension());
            openApi.setExtensions(extensions);
        };
    }
}
