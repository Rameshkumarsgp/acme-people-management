package com.acme.peoplemanagement.employee.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.peoplemanagement.auth.AuthTestSupport;
import com.acme.peoplemanagement.employee.EmployeeService;
import com.acme.peoplemanagement.employee.errors.EmailAlreadyExistsException;
import com.acme.peoplemanagement.employee.errors.EmployeeNotFoundException;
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
@Import({AuthTestSupport.class, EmployeeEndpointTest.ClockConfiguration.class})
class EmployeeEndpointTest {

    private static final String VALID_BODY = """
            {"fullName": "Asha Rao", "email": "asha.rao@acme.example", "hireDate": "2024-04-01",
             "departmentId": 3, "jobTitleId": 12, "countryCode": "IN",
             "initialSalary": {"amountMinor": 125000000, "currency": "INR"}}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService employeeService;

    @Test
    void createsAnEmployeeAndPointsToItsDetail() throws Exception {
        given(employeeService.create(any())).willReturn(sampleEmployee());

        postEmployee(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/10432"))
                .andExpect(jsonPath("$.id").value(10432))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.terminationDate").doesNotExist())
                .andExpect(jsonPath("$.currentSalary.amountMinor").value(125000000))
                .andExpect(jsonPath("$.currentSalary.currency").value("INR"));
    }

    @Test
    void readsAnEmployee() throws Exception {
        given(employeeService.get(10432L)).willReturn(sampleEmployee());

        mockMvc.perform(get("/api/employees/10432").with(login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.department.name").value("Engineering"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void namesTheFieldWhenTheNameIsBlank() throws Exception {
        postEmployee(VALID_BODY.replace("Asha Rao", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.errors[0].field").value("fullName"));
    }

    @Test
    void namesTheNestedFieldWhenTheAmountIsZero() throws Exception {
        postEmployee(VALID_BODY.replace("125000000", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialSalary.amountMinor"));
    }

    @Test
    void rejectsAnAmountOneAboveTheLargestExactInteger() throws Exception {
        postEmployee(VALID_BODY.replace("125000000", "9007199254740992"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("initialSalary.amountMinor"));
    }

    @Test
    void answersProblemJsonForAMalformedBody() throws Exception {
        postEmployee("{\"fullName\": ")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void answersProblemJsonForAnEmployeeIdThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/employees/abc").with(login()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void answers422WithTheRuleCode() throws Exception {
        given(employeeService.create(any())).willThrow(
                new SalaryRuleViolation(SalaryRuleCode.NO_CURRENT_SALARY, "No current salary."));

        postEmployee(VALID_BODY)
                .andExpect(status().is(422))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("NO_CURRENT_SALARY"))
                .andExpect(jsonPath("$.detail").value("No current salary."));
    }

    @Test
    void answers409ForADuplicateEmail() throws Exception {
        given(employeeService.create(any())).willThrow(new EmailAlreadyExistsException());

        postEmployee(VALID_BODY)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void answers400WithTheFieldForAnUnknownReference() throws Exception {
        given(employeeService.create(any()))
                .willThrow(new UnknownReferenceException("departmentId", "Unknown department."));

        postEmployee(VALID_BODY)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("departmentId"))
                .andExpect(jsonPath("$.errors[0].message").value("Unknown department."));
    }

    @Test
    void answers404ForAnEmployeeThatDoesNotExist() throws Exception {
        given(employeeService.get(7L)).willThrow(new EmployeeNotFoundException(7L));

        mockMvc.perform(get("/api/employees/7").with(login()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void requiresTheLogin() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions postEmployee(final String body) throws Exception {
        return mockMvc.perform(post("/api/employees")
                .with(login())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private static RequestPostProcessor login() {
        return httpBasic("hr-test", "test-only-password");
    }

    private static EmployeeResponse sampleEmployee() {
        return new EmployeeResponse(10432L, "Asha Rao", "asha.rao@acme.example", LocalDate.of(2024, 4, 1), null,
                EmployeeStatus.ACTIVE, new DepartmentResponse(3, "Engineering"),
                new JobTitleResponse(12, "Software Engineer"), new CountryResponse("IN", "India"),
                new CurrentSalaryResponse(LocalDate.of(2024, 4, 1), 125_000_000L, "INR"), 0L);
    }

    @TestConfiguration
    static class ClockConfiguration {

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);
        }
    }
}
