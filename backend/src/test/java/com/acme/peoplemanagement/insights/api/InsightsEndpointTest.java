package com.acme.peoplemanagement.insights.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.insights.InsightsService;
import com.acme.peoplemanagement.insights.rules.Dimension;
import com.acme.peoplemanagement.insights.rules.ExtremesOrder;
import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import com.acme.peoplemanagement.readmodel.StatusFilter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InsightsController.class)
@ActiveProfiles("test")
@Import({AuthTestSupport.class, InsightsEndpointTest.ClockConfiguration.class})
class InsightsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsightsService insightsService;

    @Test
    void returnsTheGroupsWithTheTotalAsAString() throws Exception {
        given(insightsService.insights(any())).willReturn(new InsightsResponse("COUNTRY", List.of(
                new InsightGroup("IN", "India", "INR", 2300, "18014398509481982", 63_000_000L, 255_000_000L,
                        300_000_000L, 540_800_000L))));

        getInsights("?dimension=COUNTRY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimension").value("COUNTRY"))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].key").value("IN"))
                .andExpect(jsonPath("$.groups[0].label").value("India"))
                .andExpect(jsonPath("$.groups[0].currency").value("INR"))
                .andExpect(jsonPath("$.groups[0].headcount").value(2300))
                .andExpect(jsonPath("$.groups[0].total").value("18014398509481982"))
                .andExpect(jsonPath("$.groups[0].min").value(63_000_000))
                .andExpect(jsonPath("$.groups[0].median").value(255_000_000))
                .andExpect(jsonPath("$.groups[0].mean").value(300_000_000))
                .andExpect(jsonPath("$.groups[0].max").value(540_800_000));
    }

    @Test
    void appliesTheDefaultsWhenNoParameterIsGiven() throws Exception {
        given(insightsService.insights(any())).willReturn(new InsightsResponse("OVERALL", List.of()));

        getInsights("").andExpect(status().isOk());

        final ArgumentCaptor<InsightsQuery> captor = ArgumentCaptor.forClass(InsightsQuery.class);
        verify(insightsService).insights(captor.capture());
        assertThat(captor.getValue().dimension()).isEqualTo(Dimension.OVERALL);
        assertThat(captor.getValue().filters().status()).isEqualTo(StatusFilter.ACTIVE);
    }

    @Test
    void passesEveryParameterToTheService() throws Exception {
        given(insightsService.insights(any())).willReturn(new InsightsResponse("TENURE", List.of()));

        getInsights("?dimension=tenure&countryCode=IN&departmentId=3&jobTitleId=12&currency=INR&status=ALL")
                .andExpect(status().isOk());

        final ArgumentCaptor<InsightsQuery> captor = ArgumentCaptor.forClass(InsightsQuery.class);
        verify(insightsService).insights(captor.capture());
        assertThat(captor.getValue().dimension()).isEqualTo(Dimension.TENURE);
        assertThat(captor.getValue().filters().countryCode()).isEqualTo("IN");
        assertThat(captor.getValue().filters().departmentId()).isEqualTo(3);
        assertThat(captor.getValue().filters().jobTitleId()).isEqualTo(12);
        assertThat(captor.getValue().filters().currency()).isEqualTo("INR");
        assertThat(captor.getValue().filters().status()).isEqualTo(StatusFilter.ALL);
    }

    @Test
    void answers400WithTheFieldForAnUnknownDimension() throws Exception {
        getInsights("?dimension=salary").andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("dimension"))
                .andExpect(jsonPath("$.errors[0].message").value(
                        "Dimension must be OVERALL, COUNTRY, DEPARTMENT, JOB_TITLE, TITLE_COUNTRY or TENURE."));
        verify(insightsService, never()).insights(any());
    }

    @Test
    void answers400ForAnUnknownStatusAndForAValueThatIsNotANumber() throws Exception {
        getInsights("?status=retired").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("status"));
        getInsights("?departmentId=x").andExpect(status().isBadRequest());
        verify(insightsService, never()).insights(any());
    }

    @Test
    void returnsTheExtremesWithTheirDetails() throws Exception {
        given(insightsService.extremes(any())).willReturn(new ExtremesResponse("HIGHEST", "INR", List.of(
                new ExtremeEmployee(7433L, "Arjun Rao", "Engineering", "Engineering Manager", "India",
                        540_800_000L, LocalDate.of(2025, 4, 1)))));

        getExtremes("?order=HIGHEST&currency=INR&limit=10")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order").value("HIGHEST"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.employees[0].id").value(7433))
                .andExpect(jsonPath("$.employees[0].fullName").value("Arjun Rao"))
                .andExpect(jsonPath("$.employees[0].department").value("Engineering"))
                .andExpect(jsonPath("$.employees[0].jobTitle").value("Engineering Manager"))
                .andExpect(jsonPath("$.employees[0].country").value("India"))
                .andExpect(jsonPath("$.employees[0].amountMinor").value(540_800_000))
                .andExpect(jsonPath("$.employees[0].effectiveDate").value("2025-04-01"));
    }

    @Test
    void passesTheExtremesParametersToTheService() throws Exception {
        given(insightsService.extremes(any())).willReturn(new ExtremesResponse("LOWEST", "INR", List.of()));

        getExtremes("?order=lowest&currency=INR&limit=5&countryCode=IN&departmentId=3&jobTitleId=12&status=FORMER")
                .andExpect(status().isOk());

        final ArgumentCaptor<ExtremesQuery> captor = ArgumentCaptor.forClass(ExtremesQuery.class);
        verify(insightsService).extremes(captor.capture());
        assertThat(captor.getValue().order()).isEqualTo(ExtremesOrder.LOWEST);
        assertThat(captor.getValue().limit()).isEqualTo(5);
        assertThat(captor.getValue().filters().currency()).isEqualTo("INR");
        assertThat(captor.getValue().filters().countryCode()).isEqualTo("IN");
        assertThat(captor.getValue().filters().departmentId()).isEqualTo(3);
        assertThat(captor.getValue().filters().jobTitleId()).isEqualTo(12);
        assertThat(captor.getValue().filters().status()).isEqualTo(StatusFilter.FORMER);
    }

    @Test
    void answers400ForExtremesWithoutACurrency() throws Exception {
        getExtremes("?order=HIGHEST").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
        verify(insightsService, never()).extremes(any());
    }

    @Test
    void answers400ForAnOrderOrALimitOutOfRange() throws Exception {
        getExtremes("?currency=INR&order=MIDDLE").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("order"));
        getExtremes("?currency=INR&limit=51").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("limit"));
        getExtremes("?currency=INR&limit=0").andExpect(status().isBadRequest());
        verify(insightsService, never()).extremes(any());
    }

    @Test
    void asksForTheLoginWhenThereIsNone() throws Exception {
        mockMvc.perform(get("/api/insights")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/insights/extremes?currency=INR")).andExpect(status().isUnauthorized());
    }

    private ResultActions getInsights(final String query) throws Exception {
        return mockMvc.perform(get("/api/insights" + query).with(login()));
    }

    private ResultActions getExtremes(final String query) throws Exception {
        return mockMvc.perform(get("/api/insights/extremes" + query).with(login()));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
