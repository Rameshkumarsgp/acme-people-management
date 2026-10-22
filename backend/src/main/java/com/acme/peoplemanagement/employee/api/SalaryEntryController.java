package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.EmployeeSalaryService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The salary history of an employee. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/employees/{employeeId}/salary-entries")
class SalaryEntryController {

    private final EmployeeSalaryService employeeSalaryService;

    SalaryEntryController(final EmployeeSalaryService employeeSalaryService) {
        this.employeeSalaryService = employeeSalaryService;
    }

    @GetMapping
    List<SalaryEntryResponse> list(@PathVariable final long employeeId) {
        return employeeSalaryService.list(employeeId);
    }

    @PostMapping
    ResponseEntity<SalaryEntryResponse> add(
            @PathVariable final long employeeId, @Valid @RequestBody final AddSalaryEntryRequest request) {
        final SalaryEntryResponse added = employeeSalaryService.add(employeeId, request);
        return ResponseEntity.created(URI.create("/api/employees/" + employeeId + "/salary-entries/" + added.id()))
                .body(added);
    }
}
