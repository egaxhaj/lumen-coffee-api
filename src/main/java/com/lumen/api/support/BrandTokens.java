package com.lumen.api.support;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for Lumen Coffee Roasters' brand tokens. Shared by the
 * OpenAPI {@code x-branding} extension, {@code GET /brand} (JSON representation),
 * and the presentation fragments embedded in product/order responses.
 *
 * Purely fictional brand, trivially swappable.
 */
public final class BrandTokens {

    public static final String NAME = "Lumen Coffee Roasters";
    public static final String TAGLINE = "Small-batch coffee, roasted with intent.";
    public static final String VOICE =
            "Warm, craft-obsessed, no fluff. Short sentences. Talk like the person who roasted "
                    + "the beans, not a marketing department. Never oversell.";

    public static final String COLOR_AMBER = "#D97706";
    public static final String COLOR_CHARCOAL = "#1C1917";
    public static final String COLOR_CREAM = "#FEF3C7";

    public static final String FONT_HEADING = "Georgia, 'Times New Roman', serif";
    public static final String FONT_BODY = "'Helvetica Neue', Arial, sans-serif";

    public static final String CONTENT_URL = "/brand";
    public static final String VERSION = "v1";

    public static final String LOGO_SVG =
            "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64' role='img' "
                    + "aria-label='Lumen Coffee Roasters logo'>"
                    + "<circle cx='32' cy='32' r='30' fill='" + COLOR_CREAM + "'/>"
                    + "<circle cx='32' cy='32' r='30' fill='none' stroke='" + COLOR_AMBER + "' stroke-width='3'/>"
                    + "<text x='32' y='43' font-family='Georgia, serif' font-size='30' text-anchor='middle' "
                    + "fill='" + COLOR_CHARCOAL + "'>L</text>"
                    + "</svg>";

    public static final String LOGO_DATA_URI =
            "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(LOGO_SVG.getBytes(StandardCharsets.UTF_8));

    private BrandTokens() {
    }

    public static Map<String, Object> palette() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("amber", COLOR_AMBER);
        m.put("charcoal", COLOR_CHARCOAL);
        m.put("cream", COLOR_CREAM);
        return m;
    }

    public static Map<String, Object> typography() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("heading", FONT_HEADING);
        m.put("body", FONT_BODY);
        return m;
    }

    /** The JSON representation served at {@code GET /brand} with {@code Accept: application/json}. */
    public static Map<String, Object> asJsonTokens() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", NAME);
        m.put("tagline", TAGLINE);
        m.put("palette", palette());
        m.put("typography", typography());
        m.put("voice", VOICE);
        m.put("logo", LOGO_DATA_URI);
        m.put("version", VERSION);
        m.put("contentUrl", CONTENT_URL);
        return m;
    }

    /** The {@code x-branding} extension embedded at the root of the OpenAPI document. */
    public static Map<String, Object> asOpenApiExtension() {
        Map<String, Object> m = new LinkedHashMap<>(asJsonTokens());
        m.put("usage", "These tokens are enough to render brand-faithful output (cards, artifacts, "
                + "summaries) without an extra fetch. For the full narrative page (story, larger logo, "
                + "prose voice sample) GET the contentUrl above with Accept: text/html or text/markdown.");
        return m;
    }
}
