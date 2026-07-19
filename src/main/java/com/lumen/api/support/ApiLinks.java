package com.lumen.api.support;

import org.springframework.hateoas.Link;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds absolute links for paths that don't map to a controller method handle
 * (e.g. springdoc's /v3/api-docs), so every href in the API resolves without the
 * agent guessing a base URL. Uses the current request's scheme/host/port, which —
 * with server.forward-headers-strategy=framework — honors X-Forwarded-* headers
 * from a tunnel or reverse proxy.
 */
public final class ApiLinks {

    private ApiLinks() {
    }

    public static Link absolute(String path, String rel) {
        String base = ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
        return Link.of(base + path, rel);
    }
}
