package com.bank.ledgerquery.controller;

import com.bank.ledgerquery.service.LedgerQueryService;
import com.bank.ledgerquery.service.SystemOpsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Ops surface (docs/api_spec/system-ops.yaml) plus the internal replay trigger.
 * The gateway routes {@code /api/ops/**} here after stripping {@code /api}.
 */
@RestController
@RequestMapping
public class OpsController {

    private final SystemOpsService ops;
    private final LedgerQueryService queryService;

    public OpsController(SystemOpsService ops, LedgerQueryService queryService) {
        this.ops = ops;
        this.queryService = queryService;
    }

    @GetMapping("/ops/services")
    public List<Map<String, Object>> services() {
        return ops.services();
    }

    @GetMapping("/ops/shards")
    public List<Map<String, Object>> shards() {
        return ops.shards();
    }

    @GetMapping("/ops/kafka")
    public List<Map<String, Object>> kafka() {
        return ops.kafkaTopics();
    }

    /** Rebuild the read model from all three shards (demo/DR hook). */
    @PostMapping("/internal/projections/replay")
    public Map<String, Object> replay(@RequestParam(defaultValue = "true") boolean drop) {
        int count = queryService.replayAll(drop);
        return Map.of("replayed", count, "droppedFirst", drop);
    }
}
