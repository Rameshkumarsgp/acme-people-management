package com.acme.peoplemanagement.readmodel;

/** A read request breaks a rule. The message is written for the person using the screen. */
public final class InvalidQueryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    public InvalidQueryException(final String field, final String message) {
        super(message);
        this.field = field;
    }

    /** The request parameter the message belongs to. */
    public String field() {
        return field;
    }
}
