package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.reference.CountryResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import java.time.LocalDate;

/** The employee detail. {@code version} goes back on edits and deletes to detect a stale view. */
public record EmployeeResponse(
        long id,
        String fullName,
        String email,
        LocalDate hireDate,
        LocalDate terminationDate,
        EmployeeStatus status,
        DepartmentResponse department,
        JobTitleResponse jobTitle,
        CountryResponse country,
        CurrentSalaryResponse currentSalary,
        long version) {
}
