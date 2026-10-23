package com.acme.peoplemanagement.dataload;

import org.springframework.core.env.Environment;

/** The command-line options that turn a run of the jar into a one-off job that ends when it is done. */
public final class BatchMode {

    public static final String LOAD_PROPERTY = "acme.load";
    public static final String SEED_PROPERTY = "acme.seed-csv";

    private BatchMode() {
    }

    public static boolean requestedBy(final Environment environment) {
        return environment.containsProperty(LOAD_PROPERTY) || environment.containsProperty(SEED_PROPERTY);
    }
}
