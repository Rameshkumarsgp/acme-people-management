package com.acme.peoplemanagement.employee.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
@AutoConfigureMockMvc
class EmployeeChangeEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void editsTerminatesAndDeletesAnEmployeeOverHttp() throws Exception {
        final String location = mockMvc.perform(post("/api/employees").with(login())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");

        putProfile(location, profile("Asha R. Rao", "null", 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha R. Rao"))
                .andExpect(jsonPath("$.version").value(1));

        putProfile(location, profile("Stale", "null", 0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        putProfile(location, profile("Asha R. Rao", "\"2026-09-30\"", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FORMER"))
                .andExpect(jsonPath("$.terminationDate").value("2026-09-30"))
                .andExpect(jsonPath("$.version").value(2));

        putProfile(location, profile("Asha R. Rao", "\"2023-01-01\"", 2))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("TERMINATION_BEFORE_HIRE_DATE"));

        mockMvc.perform(delete(location + "?version=1").with(login())).andExpect(status().isConflict());
        mockMvc.perform(delete(location + "?version=2").with(login())).andExpect(status().isNoContent());
        mockMvc.perform(get(location).with(login())).andExpect(status().isNotFound());
    }

    private ResultActions putProfile(final String location, final String body) throws Exception {
        return mockMvc.perform(put(location).with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String profile(final String name, final String termination, final int version) {
        return ("{\"fullName\": \"%s\", \"email\": \"change@acme.example\", \"hireDate\": \"2024-04-01\", "
                + "\"terminationDate\": %s, \"departmentId\": %d, \"jobTitleId\": %d, \"countryCode\": \"IN\", "
                + "\"version\": %d}").formatted(name, termination, idOf("department", "Engineering"),
                idOf("job_title", "Software Engineer"), version);
    }

    private String createBody() {
        return ("{\"fullName\": \"Asha Rao\", \"email\": \"change@acme.example\", \"hireDate\": \"2024-04-01\", "
                + "\"departmentId\": %d, \"jobTitleId\": %d, \"countryCode\": \"IN\", "
                + "\"initialSalary\": {\"amountMinor\": 125000000, \"currency\": \"INR\"}}")
                .formatted(idOf("department", "Engineering"), idOf("job_title", "Software Engineer"));
    }

    private int idOf(final String table, final String name) {
        final Integer id = jdbcTemplate.queryForObject(
                "select id from " + table + " where name = ?", Integer.class, name);
        return id == null ? -1 : id;
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }
}
