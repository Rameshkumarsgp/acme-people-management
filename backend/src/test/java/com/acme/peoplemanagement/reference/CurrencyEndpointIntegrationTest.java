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
class CurrencyEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheCurrenciesSortedByCode() throws Exception {
        listCurrencies()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[0].code").value("AED"))
                .andExpect(jsonPath("$[8].code").value("USD"));
    }

    @Test
    void tellsHowManyMinorUnitDigitsEachCurrencyHas() throws Exception {
        listCurrencies()
                .andExpect(jsonPath("$[0].minorUnitDigits").value(2))
                .andExpect(jsonPath("$[6].code").value("JPY"))
                .andExpect(jsonPath("$[6].minorUnitDigits").value(0));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(get("/api/reference/currencies")).andExpect(status().isUnauthorized());
    }

    private ResultActions listCurrencies() throws Exception {
        return mockMvc.perform(get("/api/reference/currencies").with(httpBasic("hr-test", "test-only-password")));
    }
}
