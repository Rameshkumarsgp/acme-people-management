package com.acme.peoplemanagement.employee.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * What HR sends to change an employee's profile, with the version they saw. It replaces the profile and never touches
 * salary. An empty termination date means the person is still employed.
 */
public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull LocalDate hireDate,
        LocalDate terminationDate,
        @NotNull Integer departmentId,
        @NotNull Integer jobTitleId,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @NotNull Long version) {
}
