package com.ledgercore.transfer.adapter.scheduling;

import com.ledgercore.shared.domain.TransferId;
import com.ledgercore.transfer.application.ReviewService;
import com.ledgercore.transfer.application.TransferRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Releases funds that would otherwise stay on hold forever if nobody reviews a flagged transfer.
 */
@Component
class ReviewExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ReviewExpiryJob.class);
    private static final int BATCH = 100;

    private final TransferRepository transfers;
    private final ReviewService reviews;
    private final Clock clock;
    private final Duration timeout;

    ReviewExpiryJob(TransferRepository transfers, ReviewService reviews, Clock clock,
                    @Value("${ledgercore.transfers.review-timeout:PT24H}") Duration timeout) {
        this.transfers = transfers;
        this.reviews = reviews;
        this.clock = clock;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${ledgercore.transfers.review-expiry-interval:PT1M}")
    void expire() {
        List<TransferId> stale = transfers.reviewsCreatedBefore(clock.instant().minus(timeout), BATCH);
        long expired = stale.stream().filter(reviews::expire).count();
        if (expired > 0) {
            log.info("Expired {} transfers left in review for longer than {}", expired, timeout);
        }
    }
}
