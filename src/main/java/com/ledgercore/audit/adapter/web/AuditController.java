package com.ledgercore.audit.adapter.web;

import com.ledgercore.audit.application.AuditEvent;
import com.ledgercore.audit.application.AuditLog;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/v1/audit")
class AuditController {

    private final AuditLog audit;

    AuditController(AuditLog audit) {
        this.audit = audit;
    }

    @GetMapping
    List<AuditEvent> byResource(@RequestParam @NotBlank String resourceId,
                                @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return audit.forResource(resourceId, limit);
    }
}
