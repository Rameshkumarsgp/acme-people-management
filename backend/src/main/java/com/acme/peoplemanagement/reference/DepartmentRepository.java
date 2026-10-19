package com.acme.peoplemanagement.reference;

import org.springframework.data.jpa.repository.JpaRepository;

interface DepartmentRepository extends JpaRepository<Department, Integer> {
}
