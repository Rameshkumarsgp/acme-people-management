package com.acme.peoplemanagement.dataload;

import com.acme.peoplemanagement.dataload.rules.FileRejectedException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** {@code --acme.load=<file>}: loads a CSV, prints what happened, and leaves a non-zero exit status if a row failed. */
@Component
@ConditionalOnProperty(BatchMode.LOAD_PROPERTY)
class LoadRunner implements ApplicationRunner, ExitCodeGenerator {

    private final EmployeeCsvLoader loader;
    private final Path file;
    private final PrintStream output;
    private final Clock clock;
    private int exitCode;

    @Autowired
    LoadRunner(
            final EmployeeCsvLoader loader, @Value("${" + BatchMode.LOAD_PROPERTY + "}") final Path file,
            final Clock clock) {
        this(loader, file, System.out, clock);
    }

    LoadRunner(final EmployeeCsvLoader loader, final Path file, final PrintStream output, final Clock clock) {
        this.loader = loader;
        this.file = file;
        this.output = output;
        this.clock = clock;
    }

    @Override
    public void run(final ApplicationArguments arguments) throws IOException {
        final long startedAt = clock.millis();
        try (Reader reader = Files.newBufferedReader(file)) {
            final LoadReport report = loader.load(reader);
            report.rejected().forEach(rejected -> output.println(
                    "Row %d [%s]: %s".formatted(rejected.rowNumber(), rejected.field(), rejected.reason())));
            output.println("Loaded %d, rejected %d, in %d ms.".formatted(
                    report.loaded(), report.rejected().size(), clock.millis() - startedAt));
            exitCode = report.hasRejections() ? 1 : 0;
        } catch (FileRejectedException exception) {
            output.println("Not loaded: " + exception.getMessage());
            exitCode = 1;
        }
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}
