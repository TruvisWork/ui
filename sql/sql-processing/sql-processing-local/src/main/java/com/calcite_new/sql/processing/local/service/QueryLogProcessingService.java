package com.calcite_new.sql.processing.local.service;

import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.QueryLog;
import com.calcite_new.core.service.DataFetchService;
import com.calcite_new.sql.processing.local.ProcessingOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryLogProcessingService {

    private final DataFetchService dataFetchService;
    private final ProcessingOrchestrator processingOrchestrator;

    @Transactional(readOnly = true)
    public void processQueryLogs() {
        int page = 0;
        int totalProcessed = 0;
        List<QueryLog> chunk;

        int pageSize = HibernateUtil.PAGE_SIZE;

        log.info("--- Starting paginated query log processing ---");

        do {
            chunk = dataFetchService.getQueryLogsPage(page, pageSize);
            if (chunk.isEmpty()) break;

            try {
                processingOrchestrator.process(chunk);
                totalProcessed += chunk.size();
                log.info("--- Processed {} logs so far ---", totalProcessed);
            } catch (Exception e) {
                log.error("--- Error processing page {}: {} ---", page, e.getMessage(), e);
            }
            page++;
        } while (chunk.size() == pageSize);

        log.info("=== Complete Processing Summary ===");
        log.info("Total logs processed: {}", totalProcessed);
        log.info("================================");
    }
}