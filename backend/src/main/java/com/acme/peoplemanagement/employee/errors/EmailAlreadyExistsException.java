package com.acme.peoplemanagement.employee.errors;

/** Another employee already has this email, whatever the case. */
public final class EmailAlreadyExistsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailAlreadyExistsException() {
        super("An employee with this email already exists.");
    }
}
