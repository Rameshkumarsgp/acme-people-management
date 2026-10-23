package com.acme.peoplemanagement.reference;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DepartmentRepository extends JpaRepository<Department, Integer> {

    Optional<Department> findByNameIgnoreCase(String name);
}
