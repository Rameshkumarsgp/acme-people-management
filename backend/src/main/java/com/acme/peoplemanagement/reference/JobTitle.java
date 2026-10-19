package com.acme.peoplemanagement.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A job title. Reference data, never changed through the API. */
@Entity
@Table(name = "job_title")
@Immutable
class JobTitle {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "name")
    private String name;

    /** For JPA. */
    protected JobTitle() {
    }

    int id() {
        return id;
    }

    String name() {
        return name;
    }
}
