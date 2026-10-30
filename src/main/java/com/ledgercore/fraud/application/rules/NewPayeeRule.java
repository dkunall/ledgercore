package com.ledgercore.fraud.application.rules;

import com.ledgercore.fraud.application.PayeeHistory;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.FraudRule;
import com.ledgercore.fraud.domain.ScoreCard;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * On its own a new payee is not suspicious enough to stop a payment; it tips borderline cases over.
 */
@Component
@Order(4)
class NewPayeeRule implements FraudRule {

    private static final int POINTS = 25;

    private final PayeeHistory history;

    NewPayeeRule(PayeeHistory history) {
        this.history = history;
    }

    @Override
    public void evaluate(FraudContext context, ScoreCard card) {
        if (context.destinationAccount() != null
            && !history.hasPaidBefore(context.source(), context.destinationAccount())) {
            card.add(POINTS, "NEW_PAYEE");
        }
    }
}
