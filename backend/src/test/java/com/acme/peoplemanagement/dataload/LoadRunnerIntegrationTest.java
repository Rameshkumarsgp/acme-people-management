package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.peoplemanagement.dataload.rules.Column;
import com.acme.peoplemanagement.support.IntegrationTest;
import com.acme.peoplemanagement.support.TestEmployees;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class LoadRunnerIntegrationTest {

    private static final String HEADER = String.join(",", Column.headers());
    private static final String ROW =
            "Asha Rao,asha@acme.example,2024-04-01,Engineering,Software Engineer,IN,INR,1250000.00,,";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path directory;

    @Autowired
    private EmployeeCsvLoader loader;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    @AfterEach
    void removeEmployees() {
        new TestEmployees(jdbcTemplate).deleteAll();
    }

    @Test
    void exitsWithZeroAndSaysHowManyWereLoadedWhenEveryRowLoads() throws IOException {
        final LoadRunner runner = runnerFor(HEADER + "\n" + ROW + "\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isZero();
        assertThat(printed()).contains("Loaded 1, rejected 0, in 0 ms.");
    }

    @Test
    void exitsNonZeroAndListsTheRejectedRowWhenARowIsRejected() throws IOException {
        final LoadRunner runner = runnerFor(HEADER + "\n" + ROW + "\n" + ROW + "\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        assertThat(printed()).contains("Row 3 [email]: EMAIL_ALREADY_EXISTS").contains("Loaded 1, rejected 1");
    }

    @Test
    void exitsNonZeroAndSaysWhyWhenTheHeaderIsWrong() throws IOException {
        final LoadRunner runner = runnerFor("fullName,email\nAsha Rao,asha@acme.example\n");

        runner.run(new DefaultApplicationArguments());

        assertThat(runner.getExitCode()).isEqualTo(1);
        assertThat(printed()).contains("Not loaded: Missing required columns");
    }

    private LoadRunner runnerFor(final String csv) throws IOException {
        final Path file = Files.writeString(directory.resolve("employees.csv"), csv);
        return new LoadRunner(loader, file, new PrintStream(output, true, StandardCharsets.UTF_8), CLOCK);
    }

    private String printed() {
        return output.toString(StandardCharsets.UTF_8);
    }
}
