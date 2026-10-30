package com.ledgercore.fraud.adapter.scheduling;

import com.ledgercore.fraud.application.DenylistIndex;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class DenylistRefreshJob {

    private final DenylistIndex index;

    DenylistRefreshJob(DenylistIndex index) {
        this.index = index;
    }

    @EventListener(ApplicationReadyEvent.class)
    void load() {
        index.rebuild();
    }

    @Scheduled(fixedDelayString = "${ledgercore.fraud.denylist-refresh:PT1M}", initialDelayString = "${ledgercore.fraud.denylist-refresh:PT1M}")
    void refresh() {
        index.rebuild();
    }
}
