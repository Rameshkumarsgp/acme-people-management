package com.acme.peoplemanagement.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

/** A currency and how many decimal places one major unit has. Reference data, never changed through the API. */
@Entity
@Table(name = "currency")
@Immutable
class Currency {

    @Id
    @Column(name = "code")
    private String code;

    @Column(name = "minor_unit_digits")
    private int minorUnitDigits;

    /** For JPA. */
    protected Currency() {
    }

    String code() {
        return code;
    }

    int minorUnitDigits() {
        return minorUnitDigits;
    }
}
