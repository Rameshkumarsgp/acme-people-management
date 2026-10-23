package com.acme.peoplemanagement.dataload.rules;

/** One row cannot be loaded. The reason is written for the person who owns the file. */
public final class RowRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public RowRejectedException(final String field, final String reason) {
        super(reason);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
