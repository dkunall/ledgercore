package com.ledgercore.audit.adapter.tracing;

import com.ledgercore.audit.application.TraceIds;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
class MicrometerTraceIds implements TraceIds {

    private final ObjectProvider<Tracer> tracer;

    MicrometerTraceIds(ObjectProvider<Tracer> tracer) {
        this.tracer = tracer;
    }

    @Override
    public Optional<String> current() {
        return Optional.ofNullable(tracer.getIfAvailable())
            .map(Tracer::currentSpan)
            .map(Span::context)
            .map(context -> context.traceId());
    }
}
