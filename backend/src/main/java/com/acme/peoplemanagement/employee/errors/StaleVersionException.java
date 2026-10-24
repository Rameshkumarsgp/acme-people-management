package com.acme.peoplemanagement.employee.errors;

/** The employee was changed since the version the caller saw. Reading the latest version is the way forward. */
public final class StaleVersionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StaleVersionException(final long employeeId) {
        super("Employee " + employeeId + " was changed by someone else since you opened it. "
                + "Reload to see the latest version.");
    }
}
