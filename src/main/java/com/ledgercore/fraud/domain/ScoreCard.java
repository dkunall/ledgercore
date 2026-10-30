package com.ledgercore.fraud.domain;

import java.util.ArrayList;
import java.util.List;

public final class ScoreCard {

    private int score;
    private boolean degraded;
    private final List<String> reasons = new ArrayList<>();

    public void add(int points, String reason) {
        score += points;
        reasons.add(reason);
    }

    public void markDegraded(String reason) {
        degraded = true;
        reasons.add(reason);
    }

    public int score() {
        return score;
    }

    public boolean degraded() {
        return degraded;
    }

    public List<String> reasons() {
        return List.copyOf(reasons);
    }
}
