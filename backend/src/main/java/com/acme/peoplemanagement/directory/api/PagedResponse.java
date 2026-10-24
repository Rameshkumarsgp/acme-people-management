package com.acme.peoplemanagement.directory.api;

import java.util.List;

/** One page of a list, with enough to draw a paginator. */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public PagedResponse {
        content = List.copyOf(content);
    }
}
