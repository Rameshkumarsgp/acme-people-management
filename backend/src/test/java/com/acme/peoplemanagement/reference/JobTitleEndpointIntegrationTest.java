package com.acme.peoplemanagement.reference;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
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
class JobTitleEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheJobTitlesSortedByName() throws Exception {
        // "Account Manager" and "Accountant" sort differently under different database collations,
        // so the first item is checked by its prefix.
        listJobTitles()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[0].name", startsWith("Account")))
                .andExpect(jsonPath("$[23].name").value("UX Researcher"));
    }

    @Test
    void givesEachJobTitleAnIdTheFormsCanSend() throws Exception {
        listJobTitles()
                .andExpect(jsonPath("$[0].id", notNullValue()))
                .andExpect(jsonPath("$[23].id", notNullValue()));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(get("/api/reference/job-titles")).andExpect(status().isUnauthorized());
    }

    private ResultActions listJobTitles() throws Exception {
        return mockMvc.perform(get("/api/reference/job-titles").with(httpBasic("hr-test", "test-only-password")));
    }
}
