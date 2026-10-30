package com.ledgercore.fraud.application;

import com.ledgercore.audit.application.AuditLog;
import com.ledgercore.fraud.domain.Decision;
import com.ledgercore.fraud.domain.DenylistKind;
import com.ledgercore.fraud.domain.FraudAssessment;
import com.ledgercore.fraud.domain.FraudContext;
import com.ledgercore.fraud.domain.FraudRule;
import com.ledgercore.fraud.domain.ScoreCard;
import com.ledgercore.shared.domain.Actor;
import com.ledgercore.shared.domain.TransferId;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FraudService {

    private final List<FraudRule> rules;
    private final FraudDecisionRepository decisions;
    private final DenylistRepository denylist;
    private final DenylistIndex index;
    private final AuditLog audit;
    private final FraudProperties properties;
    private final MeterRegistry meters;

    public FraudService(List<FraudRule> rules, FraudDecisionRepository decisions, DenylistRepository denylist,
                        DenylistIndex index, AuditLog audit, FraudProperties properties, MeterRegistry meters) {
        this.rules = rules;
        this.decisions = decisions;
        this.denylist = denylist;
        this.index = index;
        this.audit = audit;
        this.properties = properties;
        this.meters = meters;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FraudAssessment assess(FraudContext context) {
        FraudAssessment assessment = score(context);
        decisions.save(context.transferId(), assessment);
        meters.counter("ledgercore.fraud.decisions", "decision", assessment.decision().name()).increment();
        if (assessment.degraded()) {
            meters.counter("ledgercore.fraud.degraded").increment();
        }
        return assessment;
    }

    FraudAssessment score(FraudContext context) {
        ScoreCard card = new ScoreCard();
        rules.forEach(rule -> rule.evaluate(context, card));
        return new FraudAssessment(decide(card.score()), card.score(), card.reasons(), card.degraded());
    }

    @Transactional(readOnly = true)
    public Optional<FraudAssessment> decisionFor(TransferId transferId) {
        return decisions.find(transferId);
    }

    @Transactional
    public boolean deny(Actor actor, DenylistKind kind, String value, String reason) {
        boolean added = denylist.add(kind, value, reason, actor.subject());
        if (added) {
            audit.record(actor, "DENYLIST_ADDED", "denylist", kind.key(value), Map.of("reason", reason));
        }
        index.add(kind, value);
        return added;
    }

    private Decision decide(int score) {
        if (score >= properties.blockScore()) {
            return Decision.BLOCK;
        }
        return score >= properties.reviewScore() ? Decision.REVIEW : Decision.ALLOW;
    }
}
