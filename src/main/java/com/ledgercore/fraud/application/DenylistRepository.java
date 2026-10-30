package com.ledgercore.fraud.application;

import com.ledgercore.fraud.domain.DenylistKind;
import java.util.List;

public interface DenylistRepository {

    boolean add(DenylistKind kind, String value, String reason, String createdBy);

    boolean contains(DenylistKind kind, String value);

    List<String> allKeys();
}
