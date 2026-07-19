package com.lumen.api.controller;

import com.lumen.api.support.BrandTokens;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * GET /brand — Lumen's branding, representation 2 (representation 1 being the
 * x-branding OpenAPI extension). Content-negotiated: text/html (default), text/markdown,
 * application/json. Accept parsing is done manually (rather than relying on Spring's
 * built-in produces-based negotiation) so the default is deterministic for plain
 * "Accept: star/star" (i.e. wildcard) or missing-header requests, exactly as curl sends them.
 */
@RestController
public class BrandController {

    private final String brandHtml;
    private final String brandMarkdown;

    public BrandController() {
        this.brandHtml = readClasspathResource("brand/brand.html");
        this.brandMarkdown = readClasspathResource("brand/brand.md");
    }

    private static String readClasspathResource(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping("/brand")
    @Operation(
            summary = "Get Lumen's branding (content-negotiated)",
            description = "Returns the brand page in the representation requested via Accept: "
                    + "text/html (default, a full styled marketing page), text/markdown (same content "
                    + "for direct chat rendering), or application/json (structured brand tokens).",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call once, up front, if you plan to "
                            + "render any brand-styled output yourself. After that, send 'Prefer: "
                            + "brand=none' on product/order requests instead of receiving the branded "
                            + "fragment repeatedly."),
                    @ExtensionProperty(name = "preconditions", value = "None."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"x:products\",\"x:orders\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Show me this business's branding.")
            })
    )
    public ResponseEntity<?> brand(@RequestHeader(value = "Accept", required = false) String accept,
                                    @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        String a = accept == null ? "" : accept.toLowerCase(Locale.ROOT);

        if (a.contains("application/json")) {
            return conditional(ifNoneMatch, "json", MediaType.APPLICATION_JSON, BrandTokens.asJsonTokens());
        }
        if (a.contains("text/markdown")) {
            return conditional(ifNoneMatch, "md",
                    MediaType.parseMediaType("text/markdown;charset=UTF-8"), brandMarkdown);
        }
        return conditional(ifNoneMatch, "html", MediaType.TEXT_HTML, brandHtml);
    }

    /**
     * Branding only changes when {@link BrandTokens#VERSION} is bumped, so the ETag is
     * derived from it (per representation) rather than hashed — a matching If-None-Match
     * short-circuits to 304 and clients may cache for an hour.
     */
    private ResponseEntity<?> conditional(String ifNoneMatch, String variant, MediaType contentType, Object body) {
        String etag = "\"" + BrandTokens.VERSION + "-" + variant + "\"";
        ResponseEntity.BodyBuilder builder = (ifNoneMatch != null && ifNoneMatch.contains(etag))
                ? ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                : ResponseEntity.ok().contentType(contentType);
        builder.eTag(etag)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .header("Brand-Version", BrandTokens.VERSION);
        return (ifNoneMatch != null && ifNoneMatch.contains(etag)) ? builder.build() : builder.body(body);
    }
}
