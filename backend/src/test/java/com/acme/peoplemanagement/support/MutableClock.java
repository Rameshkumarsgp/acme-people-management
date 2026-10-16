package com.acme.peoplemanagement.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock tests can move forward, so time-based rules are checked without waiting. */
public final class MutableClock extends Clock {

    private Instant currentInstant;
    private final ZoneId zone;

    public MutableClock(final Instant startInstant) {
        this(startInstant, ZoneOffset.UTC);
    }

    private MutableClock(final Instant startInstant, final ZoneId zone) {
        this.currentInstant = startInstant;
        this.zone = zone;
    }

    public void advance(final Duration amount) {
        currentInstant = currentInstant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(final ZoneId newZone) {
        return new MutableClock(currentInstant, newZone);
    }

    @Override
    public Instant instant() {
        return currentInstant;
    }
}
