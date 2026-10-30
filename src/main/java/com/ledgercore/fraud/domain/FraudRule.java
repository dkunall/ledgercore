package com.ledgercore.fraud.domain;

public interface FraudRule {

    void evaluate(FraudContext context, ScoreCard card);
}
