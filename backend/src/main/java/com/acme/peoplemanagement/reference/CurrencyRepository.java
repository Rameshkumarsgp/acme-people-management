package com.acme.peoplemanagement.reference;

import org.springframework.data.jpa.repository.JpaRepository;

interface CurrencyRepository extends JpaRepository<Currency, String> {
}
