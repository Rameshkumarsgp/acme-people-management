package com.acme.peoplemanagement.employee.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class EmployeeEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void createsAnEmployeeAndReadsItFromTheLocationHeader() throws Exception {
        final String location = postEmployee(body("2024-04-01", "asha@acme.example"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currentSalary.effectiveDate").value("2024-04-01"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic("hr-test", "test-only-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@acme.example"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.country.name").value("India"));
    }

    @Test
    void answers409ForAnEmailThatDiffersOnlyInCase() throws Exception {
        postEmployee(body("2024-04-01", "same@acme.example")).andExpect(status().isCreated());

        postEmployee(body("2024-04-01", "SAME@acme.example"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void answers422AndStoresNothingForAHireDateInTheFuture() throws Exception {
        postEmployee(body("2026-10-20", "future@acme.example"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("NO_CURRENT_SALARY"));

        final Integer stored = jdbcTemplate.queryForObject(
                "select count(*) from employee where email = 'future@acme.example'", Integer.class);
        assertThat(stored).isZero();
    }

    private ResultActions postEmployee(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees")
                .with(httpBasic("hr-test", "test-only-password"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String body(final String hireDate, final String email) {
        return ("{\"fullName\": \"Asha Rao\", \"email\": \"%s\", \"hireDate\": \"%s\", "
                + "\"departmentId\": %d, \"jobTitleId\": %d, \"countryCode\": \"IN\", "
                + "\"initialSalary\": {\"amountMinor\": 125000000, \"currency\": \"INR\"}}")
                .formatted(email, hireDate,
                        idOf("department", "Engineering"), idOf("job_title", "Software Engineer"));
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }
}
