package com.ledgercore.account.application;

import java.util.Currency;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledgercore.accounts")
public record AccountProperties(@DefaultValue("INR") Set<Currency> supportedCurrencies) {
}
