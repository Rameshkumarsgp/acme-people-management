package com.acme.peoplemanagement.directory.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@AutoConfigureMockMvc
class DirectoryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void searchesFiltersAndPagesRealEmployeesOverHttp() throws Exception {
        final TestEmployees employees = new TestEmployees(jdbcTemplate);
        for (int number = 1; number <= 3; number++) {
            final long id = employees.insert("Rao Person " + number, "rao" + number + "@acme.example",
                    LocalDate.of(2024, 4, 1), null);
            employees.insertSalary(id, LocalDate.of(2024, 4, 1), number * 100_000L, "INR");
        }
        final long other = employees.insert("Someone Else", "else@acme.example", LocalDate.of(2024, 4, 1), null);
        employees.insertSalary(other, LocalDate.of(2024, 4, 1), 50L, "INR");

        mockMvc.perform(get("/api/employees?q=rao&currency=INR&sort=salary,desc&size=2").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].fullName").value("Rao Person 3"))
                .andExpect(jsonPath("$.content[0].currentSalary.amountMinor").value(300_000))
                .andExpect(jsonPath("$.content[1].fullName").value("Rao Person 2"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].country").value("India"))
                .andExpect(jsonPath("$.content[0].hireDate").value("2024-04-01"));
    }

    @Test
    void refusesToSortBySalaryWithoutACurrencyOverHttp() throws Exception {
        mockMvc.perform(get("/api/employees?sort=salary,asc").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("sort"));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }
}
