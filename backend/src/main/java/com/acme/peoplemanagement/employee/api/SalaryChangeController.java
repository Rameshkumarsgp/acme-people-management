package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.EmployeeSalaryService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The change log of an employee's salary entries, newest first. */
@RestController
@RequestMapping("/api/employees/{employeeId}/salary-changes")
class SalaryChangeController {

    private final EmployeeSalaryService employeeSalaryService;

    SalaryChangeController(final EmployeeSalaryService employeeSalaryService) {
        this.employeeSalaryService = employeeSalaryService;
    }

    @GetMapping
    List<SalaryChangeResponse> list(@PathVariable final long employeeId) {
        return employeeSalaryService.changes(employeeId);
    }
}
