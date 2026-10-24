package com.acme.peoplemanagement.employee.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.employee.EmployeeService;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
import com.acme.peoplemanagement.employee.errors.StaleVersionException;
import com.acme.peoplemanagement.employee.errors.UnknownReferenceException;
import com.acme.peoplemanagement.employee.rules.EmployeeStatus;
import com.acme.peoplemanagement.reference.CountryResponse;
import com.acme.peoplemanagement.reference.DepartmentResponse;
import com.acme.peoplemanagement.reference.JobTitleResponse;
import com.acme.peoplemanagement.salary.rules.SalaryRuleCode;
import com.acme.peoplemanagement.salary.rules.SalaryRuleViolation;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

@WebMvcTest(EmployeeController.class)
@ActiveProfiles("test")
@Import({AuthTestSupport.class, EmployeeChangeEndpointTest.ClockConfiguration.class})
class EmployeeChangeEndpointTest {

    private static final String VALID_BODY = """
            {"fullName": "Asha Rao", "email": "asha.rao@acme.example", "hireDate": "2024-04-01",
             "terminationDate": "2026-09-30", "departmentId": 3, "jobTitleId": 12, "countryCode": "IN",
             "version": 4}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void updatesAnEmployeeAndReturnsTheDetailWithTheNewVersion() throws Exception {
        given(employeeService.update(eq(10432L), any())).willReturn(sampleEmployee(5L));

        putEmployee(VALID_BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10432))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    void passesTheWholeBodyToTheService() throws Exception {
        given(employeeService.update(eq(10432L), any())).willReturn(sampleEmployee(5L));

        putEmployee(VALID_BODY).andExpect(status().isOk());

        final ArgumentCaptor<UpdateEmployeeRequest> captor = ArgumentCaptor.forClass(UpdateEmployeeRequest.class);
        verify(employeeService).update(eq(10432L), captor.capture());
        final UpdateEmployeeRequest sent = captor.getValue();
        assertThat(sent.fullName()).isEqualTo("Asha Rao");
        assertThat(sent.email()).isEqualTo("asha.rao@acme.example");
        assertThat(sent.hireDate()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(sent.terminationDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(sent.departmentId()).isEqualTo(3);
        assertThat(sent.jobTitleId()).isEqualTo(12);
        assertThat(sent.countryCode()).isEqualTo("IN");
        assertThat(sent.version()).isEqualTo(4L);
    }

    @Test
    void acceptsABodyWithoutATerminationDate() throws Exception {
        given(employeeService.update(eq(10432L), any())).willReturn(sampleEmployee(5L));

        putEmployee(VALID_BODY.replace("\"terminationDate\": \"2026-09-30\",", "")).andExpect(status().isOk());
    }

    @Test
    void answers409WithTheVersionConflictCodeWhenTheVersionIsStale() throws Exception {
        given(employeeService.update(eq(10432L), any())).willThrow(new StaleVersionException(10432L));

        putEmployee(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Reload")));
    }

    @Test
    void answers409ForAnEmailAnotherEmployeeUses() throws Exception {
        given(employeeService.update(eq(10432L), any())).willThrow(new EmailAlreadyExistsException());

        putEmployee(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void answers422WithTheRuleCodeWhenASalaryEntryFallsAfterTheTermination() throws Exception {
        given(employeeService.update(eq(10432L), any())).willThrow(new SalaryRuleViolation(
                SalaryRuleCode.SALARY_AFTER_TERMINATION, "A salary cannot be effective after the termination date."));

        putEmployee(VALID_BODY)
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("SALARY_AFTER_TERMINATION"));
    }

    @Test
    void answers400WithTheFieldForAnUnknownReference() throws Exception {
        given(employeeService.update(eq(10432L), any()))
                .willThrow(new UnknownReferenceException("departmentId", "Unknown department."));

        putEmployee(VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("departmentId"));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        given(employeeService.update(eq(10432L), any())).willThrow(new EmployeeNotFoundException(10432L));

        putEmployee(VALID_BODY).andExpect(status().isNotFound());
    }

    @Test
    void namesTheFieldWhenTheNameIsBlank() throws Exception {
        putEmployee(VALID_BODY.replace("Asha Rao", " "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
        verify(employeeService, never()).update(any(Long.class), any());
    }

    @Test
    void namesTheVersionFieldWhenItIsMissing() throws Exception {
        putEmployee(VALID_BODY.replaceAll(",\\s*\"version\": 4", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("version"));
    }

    @Test
    void deletesAnEmployeeAndAnswersNoContent() throws Exception {
        deleteEmployee("?version=3").andExpect(status().isNoContent());

        verify(employeeService).delete(10432L, 3L);
    }

    @Test
    void answers400WhenTheVersionIsMissingOnDelete() throws Exception {
        deleteEmployee("").andExpect(status().isBadRequest());

        verify(employeeService, never()).delete(any(Long.class), any(Long.class));
    }

    @Test
    void answers409WithTheVersionConflictCodeOnDelete() throws Exception {
        willThrow(new StaleVersionException(10432L)).given(employeeService).delete(10432L, 3L);

        deleteEmployee("?version=3")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
    }

    @Test
    void answers404WhenDeletingAnEmployeeThatDoesNotExist() throws Exception {
        willThrow(new EmployeeNotFoundException(10432L)).given(employeeService).delete(10432L, 3L);

        deleteEmployee("?version=3").andExpect(status().isNotFound());
    }

    @Test
    void requiresTheLoginToChangeOrDelete() throws Exception {
        mockMvc.perform(put("/api/employees/10432").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/employees/10432?version=3")).andExpect(status().isUnauthorized());
    }

    private ResultActions putEmployee(final String body) throws Exception {
        return mockMvc.perform(put("/api/employees/10432")
                .with(login()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions deleteEmployee(final String query) throws Exception {
        return mockMvc.perform(delete("/api/employees/10432" + query).with(login()));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static EmployeeResponse sampleEmployee(final long version) {
        return new EmployeeResponse(10432L, "Asha Rao", "asha.rao@acme.example", LocalDate.of(2024, 4, 1),
                LocalDate.of(2026, 9, 30), EmployeeStatus.FORMER, new DepartmentResponse(3, "Engineering"),
                new JobTitleResponse(12, "Software Engineer"), new CountryResponse("IN", "India"),
                new CurrentSalaryResponse(LocalDate.of(2024, 4, 1), 125_000_000L, "INR"), version);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
