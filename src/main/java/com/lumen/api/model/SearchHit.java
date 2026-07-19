package com.lumen.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.hateoas.server.core.Relation;

import java.util.Map;

/**
 * One matched OpenAPI operation returned by {@code GET /api/search?q=}. {@code score} is
 * used to sort results and isn't exposed in the response.
 */
@Relation(collectionRelation = "results", itemRelation = "result")
public class SearchHit {

    private final String method;
    private final String path;
    private final String operationId;
    private final String summary;
    private final String description;
    private final Map<String, Object> xLlm;

    @JsonIgnore
    private final int score;

    public SearchHit(String method, String path, String operationId, String summary, String description,
                      Map<String, Object> xLlm, int score) {
        this.method = method;
        this.path = path;
        this.operationId = operationId;
        this.summary = summary;
        this.description = description;
        this.xLlm = xLlm;
        this.score = score;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getOperationId() {
        return operationId;
    }

    public String getSummary() {
        return summary;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getxLlm() {
        return xLlm;
    }

    public int getScore() {
        return score;
    }
}
