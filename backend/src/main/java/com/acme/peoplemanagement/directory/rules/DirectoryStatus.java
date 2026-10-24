package com.acme.peoplemanagement.directory.rules;

import com.acme.peoplemanagement.directory.errors.InvalidDirectoryRequestException;
import java.util.Arrays;

/** Which employees the directory lists: those still employed, those who left, or both. */
public enum DirectoryStatus {
    ACTIVE,
    FORMER,
    ALL;

    public static DirectoryStatus parse(final String text) {
        if (text == null || text.isBlank()) {
            return ACTIVE;
        }
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(text.strip()))
                .findFirst()
                .orElseThrow(() -> new InvalidDirectoryRequestException(
                        "status", "Status must be ACTIVE, FORMER or ALL."));
    }
}
