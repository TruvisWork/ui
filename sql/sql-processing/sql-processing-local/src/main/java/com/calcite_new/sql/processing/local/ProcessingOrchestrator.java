package com.calcite_new.sql.processing.local;

import com.calcite_new.core.config.HibernateUtil;
import com.calcite_new.core.entity.QueryLog;
import com.calcite_new.sql.core.processor.QueryLogProcessor;
import com.calcite_new.sql.model.entity.SqlStatementInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessingOrchestrator {

    private final QueryLogProcessor processor;
    private final BatchPersister persister;

    private ExecutorService processingExecutor;
    private ExecutorService persistenceExecutor;


    @PostConstruct
    public void init() {
        int processingThreads = Runtime.getRuntime().availableProcessors();
        processingExecutor = Executors.newFixedThreadPool(6);
        persistenceExecutor = Executors.newFixedThreadPool(6);

        persister.configure(HibernateUtil.BATCH_SIZE, persistenceExecutor);

        log.info("ProcessingOrchestrator initialized with {} processing threads and batch size {}.",
                processingThreads, HibernateUtil.BATCH_SIZE);
    }

    public void process(List<QueryLog> records) {
        if (records.isEmpty()) {
            log.info("--- No records to process ---");
            return;
        }

        List<List<QueryLog>> batches = partition(records, HibernateUtil.BATCH_SIZE);
        log.info("--- Created {} batches with size {} ---", batches.size(), HibernateUtil.BATCH_SIZE);

        AtomicInteger processedRecords = new AtomicInteger(0);
        int totalRecords = records.size();

        for (int i = 0; i < batches.size(); i += HibernateUtil.PARALLEL_BATCHES) {
            int currentGroup = (i / HibernateUtil.PARALLEL_BATCHES) + 1;
            int totalBatchGroups = (int) Math.ceil((double) batches.size() / HibernateUtil.PARALLEL_BATCHES);
            log.info("--- Processing batch group {}/{} ---", currentGroup, totalBatchGroups);

            processBatchGroup(batches, i);
            persister.waitForCompletion();

            int currentBatchGroupSize = Math.min(HibernateUtil.PARALLEL_BATCHES * HibernateUtil.BATCH_SIZE,
                    totalRecords - i * HibernateUtil.BATCH_SIZE);
            processedRecords.addAndGet(currentBatchGroupSize);

            log.info("--- Progress: {}/{} records processed ({}%) ---",
                    processedRecords.get(),
                    totalRecords,
                    String.format("%.2f", (processedRecords.get() * 100.0) / totalRecords));
        }
        persister.flush();
        persister.waitForCompletion();
    }

    private void processBatchGroup(List<List<QueryLog>> batches, int startIndex) {
        int end = Math.min(startIndex + HibernateUtil.PARALLEL_BATCHES, batches.size());
        List<CompletableFuture<List<SqlStatementInfo>>> futures = new ArrayList<>();

/*
    log.debug("--- Starting parallel processing of batches {}-{} ---", startIndex + 1, end);
*/

        for (int j = startIndex; j < end; j++) {
            List<QueryLog> batch = batches.get(j);
            final int batchIndex = j;
/*
      log.debug("--- Submitting batch {} for processing (size: {}) ---", batchIndex + 1, batch.size());
*/

            futures.add(CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            List<SqlStatementInfo> result = processor.process(batch);
           /*       log.debug("--- Completed batch {} processing with {} results ---",
                          batchIndex + 1, result.size());*/
                            return result;
                        } catch (Exception e) {
                            log.error("--- Error processing batch {}: {} ---", batchIndex + 1, e.getMessage());
                            throw e;
                        }
                    },
                    processingExecutor
            ));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenAccept(v -> {
                        List<SqlStatementInfo> results = futures.stream()
                                .map(CompletableFuture::join)
                                .flatMap(List::stream)
                                .toList();
                        log.info("--- Persisting {} results from batch group {}-{} ---",
                                results.size(), startIndex + 1, end);
                        persister.addAll(results);
                    })
                    .orTimeout(HibernateUtil.PROCESSING_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                    .join();
        } catch (Exception e) {
            log.error("--- Failed to process batch group {}-{}: {} ---",
                    startIndex + 1, end, e.getMessage());
            cancelFutures(futures);
            throw e;
        }
    }

    private void cancelFutures(List<CompletableFuture<List<SqlStatementInfo>>> futures) {
        futures.forEach(f -> f.cancel(true));
        log.info("--- Cancelled pending futures to release resources ---");
    }

    private List<List<QueryLog>> partition(List<QueryLog> records, int batchSize) {
        List<List<QueryLog>> batches = new ArrayList<>();
        for (int i = 0; i < records.size(); i += batchSize) {
            batches.add(records.subList(i, Math.min(i + batchSize, records.size())));
        }
        log.debug("--- Partitioned {} records into {} batches ---", records.size(), batches.size());
        return batches;
    }

    @PreDestroy
    public void shutdown() {
        log.info("--- Shutting down ProcessingOrchestrator executors ---");
        shutdownExecutor(processingExecutor, "Processing");
        shutdownExecutor(persistenceExecutor, "Persistence");
        log.info("--- ProcessingOrchestrator shutdown complete ---");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    log.error("--- {} executor did not terminate ---", name);
                }
            }
            log.info("--- {} executor terminated successfully ---", name);
        } catch (InterruptedException e) {
            log.error("--- {} executor shutdown interrupted ---", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}