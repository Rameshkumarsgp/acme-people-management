package com.acme.peoplemanagement.dataload.rules;

/** The whole file cannot be loaded, for example because a column is missing. */
public final class FileRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FileRejectedException(final String reason) {
        super(reason);
    }
}
