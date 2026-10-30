package com.ledgercore.fraud.application;

import com.ledgercore.fraud.domain.BloomFilter;
import com.ledgercore.fraud.domain.DenylistKind;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * In-memory Bloom filter in front of the denylist table. Almost every lookup is a miss, and a miss
 * never touches the database. Rebuilt periodically so entries added by other instances show up.
 */
@Component
public class DenylistIndex {

    private final DenylistRepository repository;
    private final FraudProperties properties;
    private final AtomicReference<BloomFilter> filter = new AtomicReference<>();

    public DenylistIndex(DenylistRepository repository, FraudProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public void rebuild() {
        List<String> keys = repository.allKeys();
        BloomFilter fresh = BloomFilter.forCapacity(Math.max(properties.denylistCapacity(), keys.size() * 2),
            properties.denylistFalsePositiveRate());
        keys.forEach(fresh::add);
        filter.set(fresh);
    }

    public void add(DenylistKind kind, String value) {
        current().add(kind.key(value));
    }

    public boolean isDenied(DenylistKind kind, String value) {
        if (value == null || !current().mightContain(kind.key(value))) {
            return false;
        }
        return repository.contains(kind, value);
    }

    private BloomFilter current() {
        BloomFilter current = filter.get();
        if (current == null) {
            rebuild();
            current = filter.get();
        }
        return current;
    }
}
