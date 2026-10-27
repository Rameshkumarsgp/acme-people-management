package com.acme.peoplemanagement.directory.rules;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import com.acme.peoplemanagement.readmodel.StatusFilter;

/**
 * A checked directory request. The search pattern, country and currency are null when not given.
 * The page and size are within their limits, and the sort is one the directory knows.
 */
public record DirectoryQuery(
        String searchPattern,
        String countryCode,
        Integer departmentId,
        Integer jobTitleId,
        String currency,
        StatusFilter status,
        DirectorySort sort,
        int page,
        int size) {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public static DirectoryQuery from(final DirectoryParams params) {
        final String currency = blankToNull(params.currency());
        return new DirectoryQuery(
                SearchPattern.of(params.q()).orElse(null),
                blankToNull(params.countryCode()),
                params.departmentId(),
                params.jobTitleId(),
                currency,
                StatusFilter.parse(params.status()),
                DirectorySort.parse(params.sort(), currency),
                page(params.page()),
                size(params.size()));
    }

    public int offset() {
        return page * size;
    }

    private static int page(final Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new InvalidQueryException("page", "Page must be 0 or more.");
        }
        return page;
    }

    private static int size(final Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidQueryException("size", "Size must be between 1 and 100.");
        }
        return size;
    }

    private static String blankToNull(final String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
