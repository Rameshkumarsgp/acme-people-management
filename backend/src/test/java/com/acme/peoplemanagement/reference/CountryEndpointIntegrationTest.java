package com.acme.peoplemanagement.reference;

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
class CountryEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheCountriesSortedByName() throws Exception {
        listCountries()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].name").value("Australia"))
                .andExpect(jsonPath("$[9].name").value("United States"));
    }

    @Test
    void givesTheCodeNextToTheName() throws Exception {
        listCountries()
                .andExpect(jsonPath("$[0].code").value("AU"))
                .andExpect(jsonPath("$[4].name").value("India"))
                .andExpect(jsonPath("$[4].code").value("IN"));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(get("/api/reference/countries")).andExpect(status().isUnauthorized());
    }

    private ResultActions listCountries() throws Exception {
        return mockMvc.perform(get("/api/reference/countries").with(httpBasic("hr-test", "test-only-password")));
    }
}
