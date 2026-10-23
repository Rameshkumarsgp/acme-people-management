package com.acme.peoplemanagement.employee.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@AutoConfigureMockMvc
class SalaryEntryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void addingARaiseEndsTheEarlierEntryAndMakesTheRaiseCurrent() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "raise@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.current").value(true));

        mockMvc.perform(get("/api/employees/" + employeeId + "/salary-entries").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-04-01"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].endDate").value("2026-03-31"))
                .andExpect(jsonPath("$[1].current").value(false));
    }

    @Test
    void aFutureRaiseIsScheduledAndTheEarlierEntryStaysCurrent() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "future-raise@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2026-10-20\", \"amountMinor\": 150000000, \"currency\": \"USD\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduled").value(true));

        mockMvc.perform(get("/api/employees/" + employeeId + "/salary-entries").with(login()))
                .andExpect(jsonPath("$[1].current").value(true));
    }

    @Test
    void answers422ForADateThatIsAlreadyUsed() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "dup-date@acme.example");

        postEntry(employeeId, "{\"effectiveDate\": \"2024-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALARY_DATE"));
    }

    @Test
    void answers400NamingTheCurrencyForABackdatedEntryWithNoEarlierEntry() throws Exception {
        final long employeeId = employeeHiredOn("2024-04-01", "backdated@acme.example");
        postEntry(employeeId, "{\"effectiveDate\": \"2025-06-01\", \"amountMinor\": 100, \"currency\": \"INR\"}")
                .andExpect(status().isCreated());
        jdbcTemplate.update("delete from salary_entry where employee_id = ? and effective_date = ?",
                employeeId, LocalDate.of(2024, 4, 1));

        postEntry(employeeId, "{\"effectiveDate\": \"2024-06-01\", \"amountMinor\": 90}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        postEntry(-1L, "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 100}").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/employees/-1/salary-entries").with(login())).andExpect(status().isNotFound());
    }

    private long employeeHiredOn(final String hireDate, final String email) {
        final TestEmployees employees = new TestEmployees(jdbcTemplate);
        final long id = employees.insert(email, LocalDate.parse(hireDate));
        employees.insertSalary(id, LocalDate.parse(hireDate), 100_000_000L, "INR");
        return id;
    }

    private ResultActions postEntry(final long employeeId, final String body) throws Exception {
        return mockMvc.perform(post("/api/employees/" + employeeId + "/salary-entries")
                .with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }
}
