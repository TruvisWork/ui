package com.calcite_new.sql.processing.local;

import com.calcite_new.core.service.DataFetchService;
import com.calcite_new.sql.core.processor.QueryLogProcessor;
import com.calcite_new.core.entity.QueryLog;
import com.calcite_new.sql.model.entity.SqlStatementInfo;
import com.calcite_new.sql.model.enums.StatementStatus;
import com.calcite_new.sql.processing.local.repository.SqlStatementInfoRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(
        classes = QueryLogProcessingApplication.class
)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessingOrchestratorIntegrationTest {

    @MockBean
    private DataFetchService dataFetchService;

    @Autowired
    private QueryLogProcessor processor;

    @Autowired
    private BatchPersister persister;

    @Autowired
    private SqlStatementInfoRepository repository;

    private ProcessingOrchestrator orchestrator;
    private ExecutorService persistenceExecutor;

    @BeforeAll
    void setup() {
        persistenceExecutor = Executors.newSingleThreadExecutor();
        persister.configure(5, persistenceExecutor);
        orchestrator = new ProcessingOrchestrator(processor, persister);
        orchestrator.init();
    }

    @BeforeEach
    void cleanDb() {
        repository.deleteAll();
    }

    @AfterAll
    void cleanup() {
        persistenceExecutor.shutdown();
    }

    @Test
    @DisplayName("Should process and store a single DELETE query")
    void testSingleDeleteQuery() {
        // given
        QueryLog record = createQueryRecord(
                "DELETE FROM foodmart.employee WHERE TRUE;",
                "query123"
        );

        // when
        orchestrator.process(List.of(record));

        // then
        List<SqlStatementInfo> persisted = repository.findAll();
        assertThat(persisted).hasSize(1);

        SqlStatementInfo info = persisted.get(0);
        assertAll(
                () -> assertEquals("stuti", info.getUserName()),
                () -> assertEquals("DELETE", info.getStatementType().name()),
                () -> assertEquals(StatementStatus.SUCCESS, info.getStatementStatus()),
                () -> assertEquals("query123", info.getLogId())
        );
    }

    @Test
    @DisplayName("Should handle batch processing of multiple queries")
    void testBatchProcessing() {
        // given
        List<QueryLog> records = Arrays.asList(
                createQueryRecord("SELECT * FROM table1;", "query1"),
                createQueryRecord("INSERT INTO table2 VALUES (1);", "query2"),
                createQueryRecord("UPDATE table3 SET col = 1;", "query3")
        );

        // when
        orchestrator.process(records);

        // then
        List<SqlStatementInfo> persisted = repository.findAll();
        assertThat(persisted).hasSize(3);
    }

    @Test
    @DisplayName("Should handle invalid SQL queries")
    void testInvalidSqlProcessing() {
        // given
        QueryLog record = createQueryRecord(
                "INVALID SQL QUERY;;;",
                "queryInvalid"
        );

        // when
        orchestrator.process(List.of(record));

        // then
        List<SqlStatementInfo> persisted = repository.findAll();
        assertThat(persisted).hasSize(1);

        SqlStatementInfo info = persisted.get(0);
        assertEquals(StatementStatus.PARSE_ERROR, info.getStatementStatus());
    }

    @Test
    @DisplayName("Should handle large batch of queries")
    void testLargeBatchProcessing() {
        // given
        List<QueryLog> records = IntStream.range(0, 100)
                .mapToObj(i -> createQueryRecord(
                        "SELECT * FROM table" + i + ";",
                        "query" + i
                ))
                .toList();

        // when
        orchestrator.process(records);

        // then
        List<SqlStatementInfo> persisted = repository.findAll();
        assertThat(persisted).hasSize(100);
        assertThat(persisted)
                .extracting(SqlStatementInfo::getStatementStatus)
                .containsOnly(StatementStatus.SUCCESS);
    }

    private QueryLog createQueryRecord(String sql, String logId) {
        return QueryLog.builder()
                .logId(logId)
                .sessionId("session123")
                .userName("stuti")
                .database("project1")
                .schema("foodmart")
                .sqlQuery(sql)
                .startTime(System.currentTimeMillis())
                .totalExecutionTimeMs(500L)  
                .build();
    }
}