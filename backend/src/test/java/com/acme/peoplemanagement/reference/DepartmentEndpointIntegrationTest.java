package com.acme.peoplemanagement.reference;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@AutoConfigureMockMvc
class DepartmentEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheDepartmentsSortedByName() throws Exception {
        listDepartments()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].name").value("Customer Support"))
                .andExpect(jsonPath("$[9].name").value("Sales"));
    }

    @Test
    void givesEachDepartmentAnIdTheFormsCanSend() throws Exception {
        listDepartments()
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[9].id", notNullValue()));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(get("/api/reference/departments")).andExpect(status().isUnauthorized());
    }

    private ResultActions listDepartments() throws Exception {
        return mockMvc.perform(get("/api/reference/departments").with(httpBasic("hr-test", "test-only-password")));
    }
}
