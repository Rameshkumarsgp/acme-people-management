package com.acme.peoplemanagement.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.peoplemanagement.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DepartmentMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsTheTenDepartments() {
        final List<String> names = jdbcTemplate.queryForList("select name from department", String.class);

        assertThat(names).containsExactlyInAnyOrder(
                "Engineering", "Product", "Design", "Data and Analytics", "Sales",
                "Marketing", "Customer Support", "Finance", "Human Resources", "Operations");
    }

    @Test
    void givesEachDepartmentItsOwnId() {
        final Integer distinctIds = jdbcTemplate.queryForObject(
                "select count(distinct id) from department", Integer.class);

        assertThat(distinctIds).isEqualTo(10);
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> insertDepartment("   ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateNameEvenWhenOnlyTheCaseDiffers() {
        assertThatThrownBy(() -> insertDepartment("Engineering")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertDepartment("engineering")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertDepartment(final String name) {
        jdbcTemplate.update("insert into department (name) values (?)", name);
    }
}
