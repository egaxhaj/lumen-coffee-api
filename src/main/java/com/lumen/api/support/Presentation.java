package com.lumen.api.support;

/**
 * A brand-styled content fragment, ready for an agent to render as-is: an HTML
 * snippet with inline brand CSS, plus a markdown variant for chat surfaces.
 */
public record Presentation(String html, String markdown) {
}
