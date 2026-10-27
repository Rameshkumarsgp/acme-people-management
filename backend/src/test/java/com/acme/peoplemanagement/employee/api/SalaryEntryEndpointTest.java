package com.acme.peoplemanagement.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.employee.EmployeeSalaryService;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.salary.errors.CurrencyRequiredException;
import com.acme.peoplemanagement.salary.errors.SalaryEntryNotFoundException;
import com.acme.peoplemanagement.salary.rules.ChangeAction;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest({SalaryEntryController.class, SalaryChangeController.class})
@ActiveProfiles("test")
@Import({AuthTestSupport.class, SalaryEntryEndpointTest.ClockConfiguration.class})
class SalaryEntryEndpointTest {

    private static final String VALID_BODY = "{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000, "
            + "\"currency\": \"INR\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeSalaryService employeeSalaryService;

    @Test
    void addsAnEntryAndPointsToIt() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/7/salary-entries/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.current").value(true))
                .andExpect(jsonPath("$.endDate").doesNotExist());
    }

    @Test
    void acceptsARequestWithoutACurrency() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry("{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000}")
                .andExpect(status().isCreated());
    }

    @Test
    void listsTheHistory() throws Exception {
        given(employeeSalaryService.list(7L)).willReturn(List.of(sampleEntry()));

        mockMvc.perform(get("/api/employees/7/salary-entries").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(5))
                .andExpect(jsonPath("$[0].amountMinor").value(150000000))
                .andExpect(jsonPath("$[0].scheduled").value(false));
    }

    @Test
    void namesTheFieldWhenTheDateIsMissing() throws Exception {
        postEntry("{\"amountMinor\": 150000000, \"currency\": \"INR\"}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("effectiveDate"));
    }

    @Test
    void rejectsAZeroAnOverCapAndAFractionalAmount() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willReturn(sampleEntry());

        postEntry(VALID_BODY.replace("150000000", "0")).andExpect(status().isBadRequest());
        postEntry(VALID_BODY.replace("150000000", "9007199254740992")).andExpect(status().isBadRequest());
        postEntry(VALID_BODY.replace("150000000", "100.9")).andExpect(status().isBadRequest());

        verify(employeeSalaryService, never()).add(eq(7L), any());
    }

    @Test
    void rejectsALowerCaseCurrency() throws Exception {
        postEntry(VALID_BODY.replace("INR", "inr"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers422WithTheCodeForADuplicateDate() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willThrow(
                new SalaryRuleViolation(SalaryRuleCode.DUPLICATE_SALARY_DATE, "There is already a salary."));

        postEntry(VALID_BODY)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALARY_DATE"));
    }

    @Test
    void answers400WithTheCurrencyFieldWhenACurrencyIsRequired() throws Exception {
        given(employeeSalaryService.add(eq(7L), any())).willThrow(new CurrencyRequiredException());

        postEntry("{\"effectiveDate\": \"2024-06-01\", \"amountMinor\": 90}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void answers400WithTheCurrencyFieldForAnUnknownCurrency() throws Exception {
        given(employeeSalaryService.add(eq(7L), any()))
                .willThrow(new UnknownReferenceException("currency", "Unknown currency."));

        postEntry(VALID_BODY.replace("INR", "ZZZ"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message").value("Unknown currency."));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        given(employeeSalaryService.list(9L)).willThrow(new EmployeeNotFoundException(9L));

        mockMvc.perform(get("/api/employees/9/salary-entries").with(login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersProblemJsonForAnEmployeeIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc/salary-entries").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(post("/api/employees/7/salary-entries")
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void editsAnEntryAndReturnsItAsItNowSits() throws Exception {
        given(employeeSalaryService.edit(eq(7L), eq(5L), any())).willReturn(sampleEntry());

        mockMvc.perform(put("/api/employees/7/salary-entries/5").with(login())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.amountMinor").value(150000000));
    }

    @Test
    void acceptsAnEditWithoutACurrency() throws Exception {
        given(employeeSalaryService.edit(eq(7L), eq(5L), any())).willReturn(sampleEntry());

        mockMvc.perform(put("/api/employees/7/salary-entries/5").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 150000000}"))
                .andExpect(status().isOk());
    }

    @Test
    void answers422WithTheRuleCodeForARefusedEdit() throws Exception {
        given(employeeSalaryService.edit(eq(7L), eq(5L), any())).willThrow(
                new SalaryRuleViolation(SalaryRuleCode.DUPLICATE_SALARY_DATE, "A salary is already on that date."));

        mockMvc.perform(put("/api/employees/7/salary-entries/5").with(login())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("DUPLICATE_SALARY_DATE"));
    }

    @Test
    void answers404WhenTheEntryOrTheEmployeeIsNotFound() throws Exception {
        given(employeeSalaryService.edit(eq(7L), eq(9L), any())).willThrow(new SalaryEntryNotFoundException(9L));
        willThrow(new EmployeeNotFoundException(8L)).given(employeeSalaryService).delete(8L, 5L);

        mockMvc.perform(put("/api/employees/7/salary-entries/9").with(login())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/employees/8/salary-entries/5").with(login())).andExpect(status().isNotFound());
    }

    @Test
    void validatesAnEditBodyLikeAnAddBody() throws Exception {
        mockMvc.perform(put("/api/employees/7/salary-entries/5").with(login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveDate\": \"2026-04-01\", \"amountMinor\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("amountMinor"));
        verify(employeeSalaryService, never()).edit(any(Long.class), any(Long.class), any());
    }

    @Test
    void deletesAnEntryAndAnswersNoContent() throws Exception {
        mockMvc.perform(delete("/api/employees/7/salary-entries/5").with(login())).andExpect(status().isNoContent());

        verify(employeeSalaryService).delete(7L, 5L);
    }

    @Test
    void answers422WhenDeletingWouldLeaveNoCurrentSalary() throws Exception {
        willThrow(new SalaryRuleViolation(SalaryRuleCode.NO_CURRENT_SALARY, "There must be a current salary."))
                .given(employeeSalaryService).delete(7L, 5L);

        mockMvc.perform(delete("/api/employees/7/salary-entries/5").with(login()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("NO_CURRENT_SALARY"));
    }

    @Test
    void listsTheChangeLogWithTheBeforeAndAfterValues() throws Exception {
        given(employeeSalaryService.changes(7L)).willReturn(List.of(
                new SalaryChangeResponse(9L, 5L, ChangeAction.EDITED, Instant.parse("2026-10-19T09:00:00Z"),
                        new SalaryChangeResponse.Values(LocalDate.of(2026, 4, 1), 150_000_000L, "INR"),
                        new SalaryChangeResponse.Values(LocalDate.of(2026, 4, 1), 160_000_000L, "INR")),
                new SalaryChangeResponse(8L, 5L, ChangeAction.ADDED, Instant.parse("2026-10-18T09:00:00Z"),
                        null, new SalaryChangeResponse.Values(LocalDate.of(2026, 4, 1), 150_000_000L, "INR"))));

        mockMvc.perform(get("/api/employees/7/salary-changes").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("EDITED"))
                .andExpect(jsonPath("$[0].changedAt").value("2026-10-19T09:00:00Z"))
                .andExpect(jsonPath("$[0].before.amountMinor").value(150000000))
                .andExpect(jsonPath("$[0].after.amountMinor").value(160000000))
                .andExpect(jsonPath("$[1].before").doesNotExist())
                .andExpect(jsonPath("$[1].after.currency").value("INR"));
    }

    @Test
    void requiresTheLoginToEditDeleteOrReadTheChangeLog() throws Exception {
        mockMvc.perform(put("/api/employees/7/salary-entries/5").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_BODY)).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/employees/7/salary-entries/5")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/employees/7/salary-changes")).andExpect(status().isUnauthorized());
    }

    private ResultActions postEntry(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees/7/salary-entries")
                .with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static SalaryEntryResponse sampleEntry() {
        return new SalaryEntryResponse(5L, LocalDate.of(2026, 4, 1), 150_000_000L, "INR", null, true, false);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
