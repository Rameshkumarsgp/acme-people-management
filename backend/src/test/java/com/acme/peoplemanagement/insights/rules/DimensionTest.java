package com.acme.peoplemanagement.insights.rules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.acme.peoplemanagement.readmodel.InvalidQueryException;
import org.junit.jupiter.api.Test;

class DimensionTest {

    @Test
    void sumsEveryoneTogetherWhenNoDimensionIsGiven() {
        assertThat(Dimension.parse(null)).isEqualTo(Dimension.OVERALL);
        assertThat(Dimension.parse(" ")).isEqualTo(Dimension.OVERALL);
    }

    @Test
    void readsADimensionIgnoringCaseAndSpaces() {
        assertThat(Dimension.parse(" title_country ")).isEqualTo(Dimension.TITLE_COUNTRY);
    }

    @Test
    void rejectsAnUnknownDimensionNamingTheChoices() {
        assertThatExceptionOfType(InvalidQueryException.class)
                .isThrownBy(() -> Dimension.parse("SALARY; drop table employee"))
                .satisfies(exception -> {
                    assertThat(exception.field()).isEqualTo("dimension");
                    assertThat(exception.getMessage()).isEqualTo(
                            "Dimension must be OVERALL, COUNTRY, DEPARTMENT, JOB_TITLE, TITLE_COUNTRY or TENURE.");
                });
    }

    @Test
    void groupsACountryByItsCodeAndNamesItByItsName() {
        assertThat(Dimension.COUNTRY.keySql()).isEqualTo("e.country_code");
        assertThat(Dimension.COUNTRY.labelSql()).isEqualTo("c.name");
        assertThat(Dimension.COUNTRY.orderBy()).isEqualTo("group_label");
    }

    @Test
    void groupsADepartmentAndAJobTitleByTheirIds() {
        assertThat(Dimension.DEPARTMENT.keySql()).isEqualTo("e.department_id::text");
        assertThat(Dimension.DEPARTMENT.labelSql()).isEqualTo("d.name");
        assertThat(Dimension.JOB_TITLE.keySql()).isEqualTo("e.job_title_id::text");
        assertThat(Dimension.JOB_TITLE.labelSql()).isEqualTo("jt.name");
    }

    @Test
    void groupsATitleInACountryByBothAndNamesItWithBoth() {
        assertThat(Dimension.TITLE_COUNTRY.keySql()).isEqualTo("e.job_title_id || '-' || e.country_code");
        assertThat(Dimension.TITLE_COUNTRY.labelSql()).isEqualTo("jt.name || ' in ' || c.name");
    }

    @Test
    void putsEveryoneInOneGroup() {
        assertThat(Dimension.OVERALL.keySql()).isEqualTo("'ALL'");
        assertThat(Dimension.OVERALL.labelSql()).isEqualTo("'All employees'");
    }

    @Test
    void ordersTenureBandsByBandAndEveryOtherDimensionByLabel() {
        assertThat(Dimension.TENURE.orderBy()).isEqualTo("group_key");
        assertThat(Dimension.values()).filteredOn(dimension -> dimension != Dimension.TENURE)
                .allSatisfy(dimension -> assertThat(dimension.orderBy()).isEqualTo("group_label"));
    }

    @Test
    void bandsTenureInCompletedYearsUpToTheEarlierOfTodayAndTheTerminationDate() {
        assertThat(Dimension.TENURE.keySql()).contains(
                "extract(year from age(least(:asOf, coalesce(e.termination_date, :asOf)), e.hire_date))",
                "< 1 then '0'", "< 3 then '1'", "< 5 then '2'", "< 10 then '3'", "else '4'");
    }

    @Test
    void namesEachTenureBand() {
        assertThat(Dimension.TENURE.labelSql()).contains("'Under 1 year'", "'1 to under 3 years'",
                "'3 to under 5 years'", "'5 to under 10 years'", "'10 or more years'");
    }
}
