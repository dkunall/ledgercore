package com.ledgercore.fraud.application.rules;

import com.ledgercore.fraud.application.FraudProperties;
import com.ledgercore.fraud.application.VelocityCounter;
import com.ledgercore.fraud.application.VelocityUnavailableException;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.FraudRule;
import com.ledgercore.fraud.domain.ScoreCard;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Counts transfers per source account in a sliding window. If Redis is unavailable the rule fails open
 * for small amounts and sends larger ones to review, rather than blocking payments outright.
 */
@Component
@Order(2)
class VelocityRule implements FraudRule {

    private final VelocityCounter counter;
    private final FraudProperties properties;

    VelocityRule(VelocityCounter counter, FraudProperties properties) {
        this.counter = counter;
        this.properties = properties;
    }

    @Override
    public void evaluate(FraudContext context, ScoreCard card) {
        long count;
        try {
            count = counter.recordAndCount("velocity:src:" + context.source(), context.transferId().toString(),
                context.at(), properties.velocityWindow());
        } catch (VelocityUnavailableException e) {
            card.markDegraded("VELOCITY_UNAVAILABLE");
            if (context.amount().minor() >= properties.degradedReviewMinor()) {
                card.add(properties.reviewScore(), "DEGRADED_REVIEW");
            }
            return;
        }
        if (count > properties.velocityBlockCount()) {
            card.add(properties.blockScore(), "VELOCITY_BLOCK");
        } else if (count > properties.velocityReviewCount()) {
            card.add(properties.reviewScore(), "VELOCITY_HIGH");
        }
    }
}
