package com.ledgercore.idempotency.adapter.scheduling;

import com.ledgercore.idempotency.application.IdempotencyStore;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class IdempotencyPurgeJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyPurgeJob.class);

    private final IdempotencyStore store;
    private final Clock clock;
    private final Duration retention;

    IdempotencyPurgeJob(IdempotencyStore store, Clock clock,
                        @Value("${ledgercore.idempotency.retention:PT24H}") Duration retention) {
        this.store = store;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${ledgercore.idempotency.purge-interval:PT10M}")
    void purge() {
        int removed = store.purgeOlderThan(clock.instant().minus(retention));
        if (removed > 0) {
            log.info("Purged {} idempotency records older than {}", removed, retention);
        }
    }
}
