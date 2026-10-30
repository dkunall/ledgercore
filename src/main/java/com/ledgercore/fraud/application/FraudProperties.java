package com.ledgercore.fraud.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledgercore.fraud")
public record FraudProperties(
    @DefaultValue("50") int reviewScore,
    @DefaultValue("100") int blockScore,
    @DefaultValue("PT1M") Duration velocityWindow,
    @DefaultValue("20") int velocityReviewCount,
    @DefaultValue("50") int velocityBlockCount,
    @DefaultValue("1000000") long highValueMinor,
    @DefaultValue("100000") long degradedReviewMinor,
    @DefaultValue("10000") int denylistCapacity,
    @DefaultValue("0.001") double denylistFalsePositiveRate) {
}
