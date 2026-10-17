package com.acme.peoplemanagement.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A country, by its ISO 3166 code. Reference data, never changed through the API. */
@Entity
@Table(name = "country")
@Immutable
class Country {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    /** For JPA. */
    protected Country() {
    }

    String code() {
        return code;
    }

    String name() {
        return name;
    }
}
