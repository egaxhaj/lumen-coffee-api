package com.lumen.api.support;

import org.springframework.stereotype.Component;

/**
 * Implements the {@code Prefer: brand=inline|none} negotiation (RFC 7240) used by
 * product and order endpoints to decide whether to include the branded
 * {@code _embedded.presentation} fragment.
 *
 * <ul>
 *   <li>No header, or {@code Prefer: brand=inline} — include the presentation fragment.</li>
 *   <li>{@code Prefer: brand=none} — the agent already has branding; omit the fragment.</li>
 *   <li>Optional cache refinement: if the agent echoes {@code X-Brand-Version} and it matches
 *       the server's current brand version, treat it the same as {@code brand=none} — the
 *       agent's copy is current, no need to resend it.</li>
 * </ul>
 */
@Component
public class BrandPreferenceResolver {

    public record Decision(boolean includePresentation, String preferenceApplied) {
    }

    public Decision resolve(String preferHeader, String brandVersionHeader) {
        String prefer = preferHeader == null ? "" : preferHeader.toLowerCase();
        boolean explicitNone = prefer.contains("brand=none");
        boolean explicitInline = prefer.contains("brand=inline");

        if (explicitNone) {
            return new Decision(false, "brand=none");
        }
        if (!explicitInline && brandVersionHeader != null && brandVersionHeader.equals(BrandTokens.VERSION)) {
            // Agent's cached branding is current; no need to resend it.
            return new Decision(false, "brand=none");
        }
        return new Decision(true, explicitInline ? "brand=inline" : null);
    }
}
