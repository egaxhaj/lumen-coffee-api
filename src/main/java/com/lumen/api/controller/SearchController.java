package com.lumen.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumen.api.model.SearchHit;
import com.lumen.api.support.ApiLinks;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * GET /api/search?q= — capability search over this API's own live OpenAPI operations.
 * Scans summaries, descriptions, and x-llm guidance and returns ranked matches as HAL,
 * each with a usable link to the actual endpoint. This answers "search this website for
 * how to do X" without any separate search index — the OpenAPI document IS the index,
 * and it is always current because springdoc regenerates it from the controllers.
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final Set<String> HTTP_METHODS = Set.of("get", "post", "put", "delete", "patch");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${server.port:8080}")
    private int port;

    @GetMapping(produces = { MediaType.APPLICATION_JSON_VALUE, MediaTypes.HAL_JSON_VALUE, MediaTypes.HAL_FORMS_JSON_VALUE })
    @Operation(
            summary = "Search this API's capabilities",
            description = "Scans the live OpenAPI document (summaries, descriptions, x-llm guidance) for "
                    + "operations relevant to the query and returns ranked matches, each with a usable "
                    + "link to the endpoint and the x-llm guidance for using it.",
            extensions = @Extension(name = "x-llm", properties = {
                    @ExtensionProperty(name = "whenToUse", value = "Call when you know what you want to "
                            + "accomplish (e.g. 'order coffee', 'cancel an order') but not which endpoint "
                            + "does it. Faster than reading the whole OpenAPI document."),
                    @ExtensionProperty(name = "preconditions", value = "A free-text query string, q."),
                    @ExtensionProperty(name = "followUpRels", value = "[\"target\"]", parseValue = true),
                    @ExtensionProperty(name = "examplePrompt", value = "Search this API for how to cancel an order.")
            })
    )
    public CollectionModel<EntityModel<SearchHit>> search(@RequestParam("q") String q) {
        JsonNode root = fetchOpenApiDoc();
        List<String> terms = List.of(q.toLowerCase(Locale.ROOT).trim().split("\\s+"));

        List<EntityModel<SearchHit>> hits = new ArrayList<>();
        JsonNode paths = root.path("paths");

        Iterator<Map.Entry<String, JsonNode>> pathIt = paths.fields();
        while (pathIt.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathIt.next();
            String path = pathEntry.getKey();

            Iterator<Map.Entry<String, JsonNode>> methodIt = pathEntry.getValue().fields();
            while (methodIt.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodIt.next();
                String method = methodEntry.getKey().toLowerCase(Locale.ROOT);
                if (!HTTP_METHODS.contains(method)) {
                    continue;
                }
                JsonNode op = methodEntry.getValue();
                String summary = op.path("summary").asText("");
                String description = op.path("description").asText("");
                String operationId = op.path("operationId").asText("");
                JsonNode xLlmNode = op.path("x-llm");

                // Weight matches by where they land: a hit in the summary (the operation's
                // actual purpose) is a much stronger signal than an incidental mention buried
                // in another operation's x-llm guidance text (e.g. an examplePrompt referencing
                // a different action in passing).
                String summaryHay = summary.toLowerCase(Locale.ROOT);
                String descriptionHay = description.toLowerCase(Locale.ROOT);
                String operationIdHay = operationId.toLowerCase(Locale.ROOT);
                String xLlmHay = xLlmNode.toString().toLowerCase(Locale.ROOT);

                int score = 0;
                for (String term : terms) {
                    if (term.isBlank()) {
                        continue;
                    }
                    if (summaryHay.contains(term)) {
                        score += 4;
                    }
                    if (operationIdHay.contains(term)) {
                        score += 3;
                    }
                    if (descriptionHay.contains(term)) {
                        score += 2;
                    }
                    if (xLlmHay.contains(term)) {
                        score += 1;
                    }
                }
                if (score == 0) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> xLlm = xLlmNode.isMissingNode()
                        ? Map.of()
                        : mapper.convertValue(xLlmNode, Map.class);

                SearchHit hitContent = new SearchHit(method.toUpperCase(Locale.ROOT), path, operationId,
                        summary, description, xLlm, score);

                EntityModel<SearchHit> hit = EntityModel.of(hitContent);
                hit.add(ApiLinks.absolute(path, "target"));
                hits.add(hit);
            }
        }

        hits.sort((a, b) -> b.getContent().getScore() - a.getContent().getScore());
        List<EntityModel<SearchHit>> top = hits.size() > 5 ? hits.subList(0, 5) : hits;

        CollectionModel<EntityModel<SearchHit>> collection = CollectionModel.of(top);
        collection.add(ApiLinks.absolute("/api/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8), "self"));
        return collection;
    }

    private JsonNode fetchOpenApiDoc() {
        String url = "http://localhost:" + port + "/v3/api-docs";
        String json = restTemplate.getForObject(url, String.class);
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse own OpenAPI document", e);
        }
    }
}
