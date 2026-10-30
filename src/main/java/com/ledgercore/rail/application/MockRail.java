package com.ledgercore.rail.application;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a payment rail (NEFT/IMPS/UPI style). Deterministic so tests and demos can choose the outcome:
 * a destination reference ending in "000" is rejected, everything else is accepted.
 */
@Component
public class MockRail {

    public RailOutcome submit(UUID transferId, String destinationRef, long amountMinor, String currency) {
        if (destinationRef.endsWith("000")) {
            return new RailOutcome(false, null, "rail-rejected:beneficiary-invalid");
        }
        return new RailOutcome(true, "RAIL-" + transferId.toString().substring(0, 8).toUpperCase(), null);
    }

    public record RailOutcome(boolean accepted, String railReference, String reason) {
    }
}
