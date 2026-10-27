package com.acme.peoplemanagement.salary.errors;

/** The employee has no salary entry with this id. */
public final class SalaryEntryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SalaryEntryNotFoundException(final long entryId) {
        super("No salary entry with ID " + entryId + " for this employee.");
    }
}
