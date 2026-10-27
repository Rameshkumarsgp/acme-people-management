package com.acme.peoplemanagement.readmodel;

import java.util.Arrays;

/** Which employees a read model includes: those still employed, those who left, or both. */
public enum StatusFilter {
    ACTIVE,
    FORMER,
    ALL;

    public static StatusFilter parse(final String text) {
        if (text == null || text.isBlank()) {
            return ACTIVE;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(text.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryException(
                        "status", "Status must be ACTIVE, FORMER or ALL."));
    }
}
