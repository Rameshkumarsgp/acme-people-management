package com.acme.peoplemanagement.salary.rules;

/** A stored salary entry with its id. */
public record IdentifiedEntry(long id, TimelineEntry entry) {
}
