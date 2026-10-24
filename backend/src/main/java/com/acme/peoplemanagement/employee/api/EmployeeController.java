package com.acme.peoplemanagement.employee.api;

import com.acme.peoplemanagement.employee.EmployeeService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Creates, reads, edits and deletes employees. Errors are turned into problem+json by the shared exception handler. */
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

    @PutMapping("/{id}")
    EmployeeResponse update(@PathVariable final long id, @Valid @RequestBody final UpdateEmployeeRequest request) {
        return employeeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable final long id, @RequestParam final long version) {
        employeeService.delete(id, version);
        return ResponseEntity.noContent().build();
    }
}
