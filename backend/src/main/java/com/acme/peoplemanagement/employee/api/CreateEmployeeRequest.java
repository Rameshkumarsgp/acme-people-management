package com.acme.peoplemanagement.employee.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * What HR enters to create an employee. Whether the referenced rows exist is checked by the service. The termination
 * date is for an employee who already left, such as one loaded from a spreadsheet.
 */
public record CreateEmployeeRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull LocalDate hireDate,
        LocalDate terminationDate,
        @NotNull Integer departmentId,
        @NotNull Integer jobTitleId,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotNull @Valid InitialSalaryRequest initialSalary) {
}
