package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.EmployeeRow;
import com.acme.peoplemanagement.dataload.seed.SeedCsvWriter;
import com.acme.peoplemanagement.dataload.seed.SeedGenerator;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code --acme.seed-csv=<file>}: writes the 10,000-employee seed. The same day always gives the same file. */
@Component
@ConditionalOnProperty(BatchMode.SEED_PROPERTY)
class SeedRunner implements ApplicationRunner {

    static final int EMPLOYEE_COUNT = 10_000;
    static final long RANDOM_SEED = 20_261_023L;

    private final Clock clock;
    private final Path file;
    private final PrintStream output;

    @Autowired
    SeedRunner(final Clock clock, @Value("${" + BatchMode.SEED_PROPERTY + "}") final Path file) {
        this(clock, file, System.out);
    }

    SeedRunner(final Clock clock, final Path file, final PrintStream output) {
        this.clock = clock;
        this.file = file;
        this.output = output;
    }

    @Override
    public void run(final ApplicationArguments arguments) throws IOException {
        final List<EmployeeRow> rows = SeedGenerator.generate(EMPLOYEE_COUNT, RANDOM_SEED, LocalDate.now(clock));
        try (Writer writer = Files.newBufferedWriter(file)) {
            SeedCsvWriter.write(rows, writer);
        }
        output.println("Wrote %d employees to %s.".formatted(rows.size(), file));
    }
}
