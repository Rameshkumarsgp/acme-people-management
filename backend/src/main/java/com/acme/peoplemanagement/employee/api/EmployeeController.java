package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.EmployeeService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Creates and reads employees. Errors are turned into problem+json by the shared exception handler. */
@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private final EmployeeService employeeService;

    EmployeeController(final EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    ResponseEntity<EmployeeResponse> create(@Valid @RequestBody final CreateEmployeeRequest request) {
        final EmployeeResponse created = employeeService.create(request);
        // A relative Location works behind the nginx proxy, where the host the API sees is not the browser's.
        return ResponseEntity.created(URI.create("/api/employees/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    EmployeeResponse get(@PathVariable final long id) {
        return employeeService.get(id);
    }
}
