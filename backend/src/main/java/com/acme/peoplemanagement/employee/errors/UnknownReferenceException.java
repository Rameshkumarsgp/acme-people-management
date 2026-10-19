package com.acme.peoplemanagement.employee.errors;

/** A request names a department, job title, country or currency that does not exist. */
public final class UnknownReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public UnknownReferenceException(final String field, final String message) {
        super(message);
        this.field = field;
    }

    /** The request field to show the message under. */
    public String field() {
        return field;
    }
}
