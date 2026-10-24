package com.acme.peoplemanagement.directory;

import com.acme.peoplemanagement.directory.api.DirectoryRow;
import com.acme.peoplemanagement.directory.api.PagedResponse;
import com.acme.peoplemanagement.directory.rules.DirectoryQuery;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Answers a directory request with one count and one page, read in a single transaction. */
@Service
public class DirectoryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    DirectoryService(final NamedParameterJdbcTemplate jdbc, final Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PagedResponse<DirectoryRow> list(final DirectoryQuery query) {
        final DirectorySql.Statements sql = DirectorySql.build(query, LocalDate.now(clock));
        final long total = Objects.requireNonNull(jdbc.queryForObject(sql.count(), sql.parameters(), Long.class));
        final List<DirectoryRow> rows = jdbc.query(sql.page(), sql.parameters(), DirectoryService::toRow);
        final int totalPages = (int) ((total + query.size() - 1) / query.size());
        return new PagedResponse<>(rows, query.page(), query.size(), total, totalPages);
    }

    private static DirectoryRow toRow(final ResultSet row, final int rowNumber) throws SQLException {
        return new DirectoryRow(
                row.getLong("id"),
                row.getString("full_name"),
                row.getString("email"),
                row.getString("department"),
                row.getString("job_title"),
                row.getString("country"),
                row.getString("status"),
                row.getObject("hire_date", LocalDate.class),
                new DirectoryRow.Salary(row.getLong("amount_minor"), row.getString("currency_code")));
    }
}
