package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface JobTitleRepository extends JpaRepository<JobTitle, Integer> {

    Optional<JobTitle> findByNameIgnoreCase(String name);
}
