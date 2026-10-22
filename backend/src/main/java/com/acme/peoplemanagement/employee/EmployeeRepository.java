package com.acme.peoplemanagement.employee;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Reads an employee and locks the row until the transaction ends, so only one salary change per
     * employee runs at a time. A pessimistic lock does not change {@code version}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") long id);
}
