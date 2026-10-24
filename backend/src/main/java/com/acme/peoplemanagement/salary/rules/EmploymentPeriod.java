package com.acme.peoplemanagement.salary.rules;

import java.time.LocalDate;

/** When an employee is employed: from the hire date to the termination date, the last day employed, if any. */
public record EmploymentPeriod(LocalDate hireDate, LocalDate terminationDate) {
}
