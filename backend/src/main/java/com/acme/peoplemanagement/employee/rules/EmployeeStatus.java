package com.acme.peoplemanagement.employee.rules;

import java.time.LocalDate;

/** Whether an employee still works for ACME. The termination date is the last day they were employed. */
public enum EmployeeStatus {
    ACTIVE,
    FORMER;

    /** The status on a day. A null termination date means the employee has not left. */
    public static EmployeeStatus on(final LocalDate today, final LocalDate terminationDate) {
        final boolean hasLeft = terminationDate != null && terminationDate.isBefore(today);
        return hasLeft ? FORMER : ACTIVE;
    }
}
