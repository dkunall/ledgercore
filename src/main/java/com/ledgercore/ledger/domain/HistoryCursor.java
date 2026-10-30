package com.ledgercore.ledger.domain;

import com.ledgercore.shared.error.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque keyset cursor. Clients must not depend on what is inside, which leaves room to change the key later.
 */
public record HistoryCursor(long beforePostingId) {

    private static final String PREFIX = "p1:";

    public String encode() {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString((PREFIX + beforePostingId).getBytes(StandardCharsets.UTF_8));
    }

    public static HistoryCursor decode(String raw) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.UTF_8);
            if (decoded.startsWith(PREFIX)) {
                return new HistoryCursor(Long.parseLong(decoded.substring(PREFIX.length())));
            }
        } catch (IllegalArgumentException e) {
            // fall through to the same error as an unknown version
        }
        throw new InvalidRequestException("invalid-cursor", "Cursor is malformed or expired");
    }
}
