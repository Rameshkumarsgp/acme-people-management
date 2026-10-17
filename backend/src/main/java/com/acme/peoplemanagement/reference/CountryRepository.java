package com.acme.peoplemanagement.reference;

import org.springframework.data.jpa.repository.JpaRepository;

interface CountryRepository extends JpaRepository<Country, String> {
}
