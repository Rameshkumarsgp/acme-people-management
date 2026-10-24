package com.acme.peoplemanagement.directory.api;

import java.time.LocalDate;

/** One line of the directory: who, where, and what they are paid now. */
public record DirectoryRow(
        long id,
        String fullName,
        String email,
        String department,
        String jobTitle,
        String country,
        String status,
        LocalDate hireDate,
        Salary currentSalary) {

    /** The current annual salary in minor units of its own currency. */
    public record Salary(long amountMinor, String currency) {
    }
}
