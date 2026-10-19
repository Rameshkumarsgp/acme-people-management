package com.acme.peoplemanagement.salary;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface SalaryEntryRepository extends JpaRepository<SalaryEntry, Long> {

    /** The current salary: the latest entry dated on or before the given day. */
    Optional<SalaryEntry> findFirstByEmployeeIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            long employeeId, LocalDate asOf);
}
