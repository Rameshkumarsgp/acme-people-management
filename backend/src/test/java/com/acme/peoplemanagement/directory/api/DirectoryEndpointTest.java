package com.acme.peoplemanagement.directory.api;

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
import com.acme.peoplemanagement.directory.DirectoryService;
import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import com.acme.peoplemanagement.directory.rules.DirectoryStatus;
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

@WebMvcTest(DirectoryController.class)
@ActiveProfiles("test")
@Import({AuthTestSupport.class, DirectoryEndpointTest.ClockConfiguration.class})
class DirectoryEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DirectoryService directoryService;

    @Test
    void returnsThePageInItsEnvelope() throws Exception {
        given(directoryService.list(any())).willReturn(new PagedResponse<>(List.of(sampleRow()), 1, 20, 41L, 3));

        getDirectory("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(10432))
                .andExpect(jsonPath("$.content[0].fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.content[0].department").value("Engineering"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.content[0].hireDate").value("2024-04-01"))
                .andExpect(jsonPath("$.content[0].currentSalary.amountMinor").value(125_000_000))
                .andExpect(jsonPath("$.content[0].currentSalary.currency").value("INR"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(41))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void appliesTheDefaultsWhenNoParameterIsGiven() throws Exception {
        given(directoryService.list(any())).willReturn(emptyPage());

        getDirectory("").andExpect(status().isOk());

        final DirectoryQuery query = capturedQuery();
        assertThat(query.status()).isEqualTo(DirectoryStatus.ACTIVE);
        assertThat(query.page()).isZero();
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.sort().orderBy()).isEqualTo("lower(e.full_name) asc, e.id");
    }

    @Test
    void passesEveryParameterToTheService() throws Exception {
        given(directoryService.list(any())).willReturn(emptyPage());

        getDirectory("?q=rao&countryCode=IN&departmentId=3&jobTitleId=12&currency=INR&status=ALL"
                + "&sort=salary,desc&page=2&size=50").andExpect(status().isOk());

        final DirectoryQuery query = capturedQuery();
        assertThat(query.searchPattern()).isEqualTo("%rao%");
        assertThat(query.countryCode()).isEqualTo("IN");
        assertThat(query.departmentId()).isEqualTo(3);
        assertThat(query.jobTitleId()).isEqualTo(12);
        assertThat(query.currency()).isEqualTo("INR");
        assertThat(query.status()).isEqualTo(DirectoryStatus.ALL);
        assertThat(query.sort().orderBy()).isEqualTo("cs.amount_minor desc, e.id");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void answers400WithTheFieldWhenTheSizeIsTooLarge() throws Exception {
        assertInvalid("?size=101", "size", "Size must be between 1 and 100.");
    }

    @Test
    void answers400WithTheFieldWhenThePageIsNegative() throws Exception {
        assertInvalid("?page=-1", "page", "Page must be 0 or more.");
    }

    @Test
    void answers400ForASortFieldThatIsNotOnTheList() throws Exception {
        assertInvalid("?sort=password,asc", "sort", "Sort by one of fullName, department, jobTitle, country, "
                + "hireDate, salary.");
    }

    @Test
    void answers400WhenSortingBySalaryWithoutACurrency() throws Exception {
        assertInvalid("?sort=salary,desc", "sort", "Sorting by salary needs a currency, because salaries in "
                + "different currencies cannot be compared.");
    }

    @Test
    void answers400ForAnUnknownStatus() throws Exception {
        assertInvalid("?status=retired", "status", "Status must be ACTIVE, FORMER or ALL.");
    }

    @Test
    void answers400ForSearchTextOverAHundredCharacters() throws Exception {
        assertInvalid("?q=" + "x".repeat(101), "q", "Search text must be at most 100 characters.");
    }

    @Test
    void answers400ForAValueThatIsNotANumber() throws Exception {
        getDirectory("?page=abc").andExpect(status().isBadRequest());
        getDirectory("?departmentId=x").andExpect(status().isBadRequest());
        verify(directoryService, never()).list(any());
    }

    @Test
    void asksForTheLoginWhenThereIsNone() throws Exception {
        mockMvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }

    private void assertInvalid(final String query, final String field, final String message) throws Exception {
        getDirectory(query)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value(field))
                .andExpect(jsonPath("$.errors[0].message").value(message));
        verify(directoryService, never()).list(any());
    }

    private ResultActions getDirectory(final String query) throws Exception {
        return mockMvc.perform(get("/api/employees" + query).with(login()));
    }

    private DirectoryQuery capturedQuery() {
        final ArgumentCaptor<DirectoryQuery> captor = ArgumentCaptor.forClass(DirectoryQuery.class);
        verify(directoryService).list(captor.capture());
        return captor.getValue();
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static PagedResponse<DirectoryRow> emptyPage() {
        return new PagedResponse<>(List.of(), 0, 20, 0L, 0);
    }

    private static DirectoryRow sampleRow() {
        return new DirectoryRow(10432L, "Asha Rao", "asha.rao@acme.example", "Engineering", "Software Engineer",
                "India", "ACTIVE", LocalDate.of(2024, 4, 1), new DirectoryRow.Salary(125_000_000L, "INR"));
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
