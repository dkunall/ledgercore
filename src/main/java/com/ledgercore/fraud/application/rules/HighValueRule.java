package com.ledgercore.fraud.application.rules;

import com.ledgercore.fraud.application.FraudProperties;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.FraudRule;
import com.ledgercore.fraud.domain.ScoreCard;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
class HighValueRule implements FraudRule {

    private final FraudProperties properties;

    HighValueRule(FraudProperties properties) {
        this.properties = properties;
    }

    @Override
    public void evaluate(FraudContext context, ScoreCard card) {
        if (context.amount().minor() >= properties.highValueMinor()) {
            card.add(properties.reviewScore(), "HIGH_VALUE");
        }
    }
}
