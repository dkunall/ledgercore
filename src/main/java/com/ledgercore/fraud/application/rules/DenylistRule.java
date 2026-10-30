package com.ledgercore.fraud.application.rules;

import com.ledgercore.fraud.application.DenylistIndex;
import com.ledgercore.fraud.domain.DenylistKind;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.FraudRule;
import com.ledgercore.fraud.domain.ScoreCard;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
class DenylistRule implements FraudRule {

    private static final int POINTS = 100;

    private final DenylistIndex denylist;

    DenylistRule(DenylistIndex denylist) {
        this.denylist = denylist;
    }

    @Override
    public void evaluate(FraudContext context, ScoreCard card) {
        if (denylist.isDenied(DenylistKind.ACCOUNT, context.source().toString())) {
            card.add(POINTS, "DENYLISTED_SOURCE");
        }
        if (denylist.isDenied(DenylistKind.ACCOUNT, context.destinationKey())
            || denylist.isDenied(DenylistKind.DESTINATION, context.destinationKey())) {
            card.add(POINTS, "DENYLISTED_DESTINATION");
        }
        if (denylist.isDenied(DenylistKind.DEVICE, context.deviceId())) {
            card.add(POINTS, "DENYLISTED_DEVICE");
        }
    }
}
