package com.acme.peoplemanagement.directory.rules;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import java.util.Optional;

/** Turns what the user typed into a LIKE pattern that matches it anywhere, with its wildcards made literal. */
public final class SearchPattern {

    private static final int MAX_LENGTH = 100;

    private SearchPattern() {
    }

    public static Optional<String> of(final String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        final String trimmed = text.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new InvalidDirectoryRequestException("q", "Search text must be at most 100 characters.");
        }
        final String literal = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return Optional.of("%" + literal + "%");
    }
}
