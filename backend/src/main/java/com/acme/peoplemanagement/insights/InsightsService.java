package com.acme.peoplemanagement.insights;

import com.acme.peoplemanagement.insights.api.ExtremeEmployee;
import com.acme.peoplemanagement.insights.api.ExtremesResponse;
import com.acme.peoplemanagement.insights.api.InsightGroup;
import com.acme.peoplemanagement.insights.api.InsightsResponse;
import com.acme.peoplemanagement.insights.rules.ExtremesQuery;
import com.acme.peoplemanagement.insights.rules.InsightsQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers insights requests from the current salaries, computing every statistic in the database. */
@Service
public class InsightsService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    InsightsService(final NamedParameterJdbcTemplate jdbc, final Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InsightsResponse insights(final InsightsQuery query) {
        final InsightsSql.Statement sql = InsightsSql.build(query, LocalDate.now(clock));
        final List<InsightGroup> groups = jdbc.query(sql.text(), sql.parameters(), InsightsService::toGroup);
        return new InsightsResponse(query.dimension().name(), groups);
    }

    @Transactional(readOnly = true)
    public ExtremesResponse extremes(final ExtremesQuery query) {
        final ExtremesSql.Statement sql = ExtremesSql.build(query, LocalDate.now(clock));
        final List<ExtremeEmployee> employees = jdbc.query(sql.text(), sql.parameters(), InsightsService::toEmployee);
        return new ExtremesResponse(query.order().name(), query.filters().currency(), employees);
    }

    private static InsightGroup toGroup(final ResultSet row, final int rowNumber) throws SQLException {
        return new InsightGroup(
                row.getString("group_key"),
                row.getString("group_label"),
                row.getString("currency"),
                row.getLong("headcount"),
                row.getBigDecimal("total").toPlainString(),
                row.getLong("min_amount"),
                row.getLong("median_amount"),
                row.getLong("mean_amount"),
                row.getLong("max_amount"));
    }

    private static ExtremeEmployee toEmployee(final ResultSet row, final int rowNumber) throws SQLException {
        return new ExtremeEmployee(
                row.getLong("id"),
                row.getString("full_name"),
                row.getString("department"),
                row.getString("job_title"),
                row.getString("country"),
                row.getLong("amount_minor"),
                row.getObject("effective_date", LocalDate.class));
    }
}
