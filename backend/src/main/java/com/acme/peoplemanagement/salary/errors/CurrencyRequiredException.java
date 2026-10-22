package com.acme.peoplemanagement.salary.errors;

/** A currency was not given and there is no earlier entry to take it from. */
public final class CurrencyRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CurrencyRequiredException() {
        super("A currency is required, because there is no earlier salary to take it from.");
    }

    /** The request field to show the message under. */
    public String field() {
        return "currency";
    }
}
