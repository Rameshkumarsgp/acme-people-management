package com.acme.peoplemanagement.dataload;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

class SeedRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    private Path directory;

    @Test
    void writesTenThousandEmployeesAndAHeaderAndSaysSo() throws IOException {
        final Path file = directory.resolve("seed.csv");
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        new SeedRunner(CLOCK, file, new PrintStream(output, true, StandardCharsets.UTF_8))
                .run(new DefaultApplicationArguments());

        assertThat(Files.readAllLines(file)).hasSize(10_001);
        assertThat(output.toString(StandardCharsets.UTF_8)).contains("Wrote 10000 employees to " + file);
    }

    @Test
    void writesTheSameBytesEveryTimeOnTheSameDay() throws IOException {
        final Path first = directory.resolve("first.csv");
        final Path second = directory.resolve("second.csv");

        run(first);
        run(second);

        assertThat(Files.readAllBytes(first)).isEqualTo(Files.readAllBytes(second));
    }

    private static void run(final Path file) throws IOException {
        new SeedRunner(CLOCK, file, new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8))
                .run(new DefaultApplicationArguments());
    }
}
