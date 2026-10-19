package com.acme.peoplemanagement.reference;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only lists that the forms and filters need. */
@RestController
@RequestMapping("/api/reference")
class ReferenceController {

    private final CurrencyRepository currencyRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;

    ReferenceController(
            final CurrencyRepository currencyRepository,
            final CountryRepository countryRepository,
            final DepartmentRepository departmentRepository,
            final JobTitleRepository jobTitleRepository) {
        this.currencyRepository = currencyRepository;
        this.countryRepository = countryRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
    }

    @GetMapping("/currencies")
    List<CurrencyResponse> currencies() {
        return currencyRepository.findAll(Sort.by("code")).stream()
                .map(currency -> new CurrencyResponse(currency.code(), currency.minorUnitDigits()))
                .toList();
    }

    @GetMapping("/countries")
    List<CountryResponse> countries() {
        return countryRepository.findAll(byName()).stream()
                .map(country -> new CountryResponse(country.code(), country.name()))
                .toList();
    }

    @GetMapping("/departments")
    List<DepartmentResponse> departments() {
        return departmentRepository.findAll(byName()).stream()
                .map(department -> new DepartmentResponse(department.id(), department.name()))
                .toList();
    }

    @GetMapping("/job-titles")
    List<JobTitleResponse> jobTitles() {
        return jobTitleRepository.findAll(byName()).stream()
                .map(jobTitle -> new JobTitleResponse(jobTitle.id(), jobTitle.name()))
                .toList();
    }

    /** Names are sorted ignoring case, so "HR Manager" does not land after every lower-case name. */
    private static Sort byName() {
        return Sort.by(Sort.Order.asc("name").ignoreCase());
    }
}
