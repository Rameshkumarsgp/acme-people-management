package com.acme.peoplemanagement.insights.rules;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import java.util.Arrays;

/** Which end of the pay scale the extremes list starts from. */
public enum ExtremesOrder {
    HIGHEST("desc"),
    LOWEST("asc");

    private final String direction;

    ExtremesOrder(final String direction) {
        this.direction = direction;
    }

    public static ExtremesOrder parse(final String text) {
        if (text == null || text.isBlank()) {
            return HIGHEST;
        }
        return Arrays.stream(values())
                .filter(order -> order.name().equalsIgnoreCase(text.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidQueryException("order", "Order must be HIGHEST or LOWEST."));
    }

    /** The SQL sort direction of the amount. */
    public String direction() {
        return direction;
    }
}
