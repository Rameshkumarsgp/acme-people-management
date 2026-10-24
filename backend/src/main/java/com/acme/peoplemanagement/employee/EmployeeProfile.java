package com.acme.peoplemanagement.employee;

import java.time.LocalDate;

/** The details that describe an employee, after the request has been validated. */
record EmployeeProfile(
        String fullName,
        String email,
        LocalDate hireDate,
        LocalDate terminationDate,
        int departmentId,
        int jobTitleId,
        String countryCode) {
}
