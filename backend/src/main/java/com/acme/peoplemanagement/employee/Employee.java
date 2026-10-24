package com.acme.peoplemanagement.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;

/**
 * An employee. The department, job title and country are stored as plain keys, because the reference
 * entities are private to their package. The database foreign keys protect them.
 */
@Entity
@Table(name = "employee")
class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "email")
    private String email;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "department_id")
    private int departmentId;

    @Column(name = "job_title_id")
    private int jobTitleId;

    @Column(name = "country_code")
    private String countryCode;

    @Version
    @Column(name = "version")
    private long version;

    /** For JPA. */
    protected Employee() {
    }

    Employee(final EmployeeProfile profile) {
        assign(profile);
    }

    /** Replaces the profile. The version is bumped by JPA when the change is written. */
    void applyProfile(final EmployeeProfile profile) {
        assign(profile);
    }

    private void assign(final EmployeeProfile profile) {
        this.fullName = profile.fullName();
        this.email = profile.email();
        this.hireDate = profile.hireDate();
        this.terminationDate = profile.terminationDate();
        this.departmentId = profile.departmentId();
        this.jobTitleId = profile.jobTitleId();
        this.countryCode = profile.countryCode();
    }

    long id() {
        return id;
    }

    String fullName() {
        return fullName;
    }

    String email() {
        return email;
    }

    LocalDate hireDate() {
        return hireDate;
    }

    LocalDate terminationDate() {
        return terminationDate;
    }

    int departmentId() {
        return departmentId;
    }

    int jobTitleId() {
        return jobTitleId;
    }

    String countryCode() {
        return countryCode;
    }

    long version() {
        return version;
    }
}
