package com.acme.peoplemanagement.employee.errors;

/** No employee has the requested id. */
public final class EmployeeNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmployeeNotFoundException(final long id) {
        super("No employee has the id " + id + ".");
    }
}
