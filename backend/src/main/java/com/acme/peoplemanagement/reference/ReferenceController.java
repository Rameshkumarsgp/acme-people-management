package com.acme.peoplemanagement.reference;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only lists that the forms and filters need. Departments, titles and countries join here. */
@RestController
@RequestMapping("/api/reference")
class ReferenceController {

    private final CurrencyRepository currencyRepository;

    ReferenceController(final CurrencyRepository currencyRepository) {
        this.currencyRepository = currencyRepository;
    }

    @GetMapping("/currencies")
    List<CurrencyResponse> currencies() {
        return currencyRepository.findAll(Sort.by("code")).stream()
                .map(currency -> new CurrencyResponse(currency.code(), currency.minorUnitDigits()))
                .toList();
    }
}
