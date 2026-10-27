package com.acme.peoplemanagement.insights.api;

import java.util.List;

/** The groups of one dimension, each in its own currency. */
public record InsightsResponse(String dimension, List<InsightGroup> groups) {

    public InsightsResponse {
        groups = List.copyOf(groups);
    }
}
