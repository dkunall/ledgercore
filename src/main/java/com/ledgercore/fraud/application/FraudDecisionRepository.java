package com.ledgercore.fraud.application;

import com.ledgercore.fraud.domain.FraudAssessment;
import com.ledgercore.shared.domain.TransferId;
import java.util.Optional;

public interface FraudDecisionRepository {

    void save(TransferId transferId, FraudAssessment assessment);

    Optional<FraudAssessment> find(TransferId transferId);
}
