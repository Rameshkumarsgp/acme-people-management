package com.acme.peoplemanagement.salary;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalaryEntryChangeRepository extends JpaRepository<SalaryEntryChange, Long> {

    List<SalaryEntryChange> findByEmployeeIdOrderByChangedAtDescIdDesc(long employeeId);
}
