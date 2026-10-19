package com.acme.peoplemanagement.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A department of the company. Reference data, never changed through the API. */
@Entity
@Table(name = "department")
@Immutable
class Department {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    /** For JPA. */
    protected Department() {
    }

    int id() {
        return id;
    }

    String name() {
        return name;
    }
}
