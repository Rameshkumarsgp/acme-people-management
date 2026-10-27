package com.acme.peoplemanagement.insights.api;

import java.util.List;

/** The highest or lowest paid employees of one currency, in rank order. */
public record ExtremesResponse(String order, String currency, List<ExtremeEmployee> employees) {

    public ExtremesResponse {
        employees = List.copyOf(employees);
    }
}
