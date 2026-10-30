package com.ledgercore.audit.application;

import java.util.Optional;

public interface TraceIds {

    Optional<String> current();
}
