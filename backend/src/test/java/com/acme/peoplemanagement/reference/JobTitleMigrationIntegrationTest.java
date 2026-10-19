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
class JobTitleMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void loadsTheTwentyFourJobTitlesAndNoLevels() {
        final List<String> titles = jdbcTemplate.queryForList("select name from job_title", String.class);

        assertThat(titles).containsExactlyInAnyOrder(
                "Software Engineer", "QA Engineer", "DevOps Engineer", "Engineering Manager",
                "Data Analyst", "Data Scientist",
                "Product Manager", "Product Designer", "UX Researcher",
                "Sales Executive", "Account Manager", "Sales Manager",
                "Marketing Specialist", "Content Writer",
                "Customer Support Agent", "Support Team Lead",
                "Accountant", "Financial Analyst", "Finance Manager",
                "Recruiter", "HR Business Partner", "HR Manager",
                "Operations Analyst", "Operations Manager");
        assertThat(titles).noneMatch(title -> title.startsWith("Senior") || title.startsWith("Junior"));
    }

    @Test
    void rejectsABlankName() {
        assertThatThrownBy(() -> insertJobTitle("")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsADuplicateNameEvenWhenOnlyTheCaseDiffers() {
        assertThatThrownBy(() -> insertJobTitle("Accountant")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertJobTitle("ACCOUNTANT")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertJobTitle(final String name) {
        jdbcTemplate.update("insert into job_title (name) values (?)", name);
    }
}
