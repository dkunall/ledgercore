package com.ledgercore.fraud.application;

import java.time.Duration;
import java.time.Instant;

public interface VelocityCounter {

    /**
     * Records {@code member} at {@code at} and returns how many members fall in the trailing window.
     *
     * @throws VelocityUnavailableException when the backing store does not answer in time
     */
    long recordAndCount(String key, String member, Instant at, Duration window);
}
