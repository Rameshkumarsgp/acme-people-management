package com.acme.peoplemanagement.insights.api;

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
class InsightsEndpointIntegrationTest {

    private static final LocalDate HIRED = LocalDate.of(2024, 4, 1);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void slicesRealSalariesByCountryAndRanksThemOverHttp() throws Exception {
        final TestEmployees employees = new TestEmployees(jdbcTemplate);
        for (int number = 1; number <= 3; number++) {
            final long id = employees.insert("Person " + number, "p" + number + "@acme.example", HIRED, null);
            employees.insertSalary(id, HIRED, number * 100_000L, "INR");
        }

        mockMvc.perform(get("/api/insights?dimension=COUNTRY").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].label").value("India"))
                .andExpect(jsonPath("$.groups[0].currency").value("INR"))
                .andExpect(jsonPath("$.groups[0].headcount").value(3))
                .andExpect(jsonPath("$.groups[0].total").value("600000"))
                .andExpect(jsonPath("$.groups[0].min").value(100_000))
                .andExpect(jsonPath("$.groups[0].median").value(200_000))
                .andExpect(jsonPath("$.groups[0].mean").value(200_000))
                .andExpect(jsonPath("$.groups[0].max").value(300_000));

        mockMvc.perform(get("/api/insights/extremes?order=HIGHEST&currency=INR&limit=2").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employees.length()").value(2))
                .andExpect(jsonPath("$.employees[0].fullName").value("Person 3"))
                .andExpect(jsonPath("$.employees[0].amountMinor").value(300_000))
                .andExpect(jsonPath("$.employees[0].effectiveDate").value("2024-04-01"))
                .andExpect(jsonPath("$.employees[1].fullName").value("Person 2"));
    }

    @Test
    void refusesToRankWithoutACurrencyOverHttp() throws Exception {
        mockMvc.perform(get("/api/insights/extremes").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }
}
