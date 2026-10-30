package com.ledgercore.fraud.domain;

import java.util.List;

public record FraudAssessment(Decision decision, int score, List<String> reasons, boolean degraded) {

    public FraudAssessment {
        reasons = List.copyOf(reasons);
    }

    public static FraudAssessment notAssessed() {
        return new FraudAssessment(Decision.ALLOW, 0, List.of("NOT_ASSESSED"), false);
    }
}
