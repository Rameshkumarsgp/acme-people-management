package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * The way other features read reference data. The entities and repositories stay private to this package,
 * so a feature can never change a reference row or depend on how it is stored.
 */
@Service
public class ReferenceLookup {

    private final CurrencyRepository currencyRepository;
    private final CountryRepository countryRepository;
    private final DepartmentRepository departmentRepository;
    private final JobTitleRepository jobTitleRepository;

    ReferenceLookup(
            final CurrencyRepository currencyRepository,
            final CountryRepository countryRepository,
            final DepartmentRepository departmentRepository,
            final JobTitleRepository jobTitleRepository) {
        this.currencyRepository = currencyRepository;
        this.countryRepository = countryRepository;
        this.departmentRepository = departmentRepository;
        this.jobTitleRepository = jobTitleRepository;
    }

    public Optional<DepartmentResponse> department(final int id) {
        return departmentRepository.findById(id).map(ReferenceLookup::toResponse);
    }

    public Optional<DepartmentResponse> departmentByName(final String name) {
        return departmentRepository.findByNameIgnoreCase(name.strip()).map(ReferenceLookup::toResponse);
    }

    public Optional<JobTitleResponse> jobTitle(final int id) {
        return jobTitleRepository.findById(id).map(ReferenceLookup::toResponse);
    }

    public Optional<JobTitleResponse> jobTitleByName(final String name) {
        return jobTitleRepository.findByNameIgnoreCase(name.strip()).map(ReferenceLookup::toResponse);
    }

    public Optional<CountryResponse> country(final String code) {
        return countryRepository.findById(code)
                .map(country -> new CountryResponse(country.code(), country.name()));
    }

    public Optional<CurrencyResponse> currency(final String code) {
        return currencyRepository.findById(code)
                .map(currency -> new CurrencyResponse(currency.code(), currency.minorUnitDigits()));
    }

    private static DepartmentResponse toResponse(final Department department) {
        return new DepartmentResponse(department.id(), department.name());
    }

    private static JobTitleResponse toResponse(final JobTitle jobTitle) {
        return new JobTitleResponse(jobTitle.id(), jobTitle.name());
    }
}
