package com.calcite_new.sql.processing.local.controller;

import com.calcite_new.sql.processing.local.service.QueryLogProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query-processing")
@RequiredArgsConstructor
public class QueryProcessingController {

    private final QueryLogProcessingService queryLogProcessingService;

    @PostMapping("/process")
    public ResponseEntity<String> triggerProcessing() {
        queryLogProcessingService.processQueryLogs();
        return ResponseEntity.ok("Query log processing initiated");
    }
}
