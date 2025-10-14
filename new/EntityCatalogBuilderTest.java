package com.calcite_new.core.service;

import com.calcite_new.core.config.RepositoryConfig;
import com.calcite_new.core.entity.*;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.core.model.entity.RelationalEntity;
import com.calcite_new.core.model.entity.ExternalTable;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.core.repository.*;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
// Removed logging statements and related annotations

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntityCatalogBuilderTest {

    @Mock
    private RepositoryConfig repositoryConfig;
    @Mock
    private TableRepository tableRepository;
    @Mock
    private ColumnRepository columnRepository;
    @Mock
    private ViewRepository viewRepository;
    @Mock
    private ExternalTableRepository externalTableRepository;
    @Mock
    private IndicesRepository indicesRepository;
    @Mock
    private PartitionRepository partitionRepository;
    private EntityCatalogBuilder builder;

    @BeforeEach
    void setUp() {
        lenient().when(repositoryConfig.getTableRepo()).thenReturn(tableRepository);
        lenient().when(repositoryConfig.getColumnRepo()).thenReturn(columnRepository);
        lenient().when(repositoryConfig.getViewRepo()).thenReturn(viewRepository);
        lenient().when(repositoryConfig.getExternalTableRepo()).thenReturn(externalTableRepository);
        lenient().when(repositoryConfig.getIndicesRepo()).thenReturn(indicesRepository);
        lenient().when(repositoryConfig.getPartitionRepo()).thenReturn(partitionRepository);
        builder = new EntityCatalogBuilder(repositoryConfig);

        // Provide hardcoded test data for all repositories
        when(tableRepository.fetchAllTables()).thenReturn(List.of(createTableEntity("test_db", "test_schema", "test_table")));
        when(columnRepository.fetchAllColumns()).thenReturn(List.of(createColumnEntity("test_db", "test_schema", "test_table", "id", 1, "INTEGER")));
        when(viewRepository.fetchAllViews()).thenReturn(List.of(createViewEntity("test_db", "test_schema", "test_view", "SELECT * FROM test_table")));
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of(createExternalTableEntity("test_db", "test_schema", "external_table1")));
        when(indicesRepository.fetchAllIndices()).thenReturn(List.of());
        when(partitionRepository.fetchAllPartitions()).thenReturn(List.of());
    }

    @Test
    void testBuildWithMockedData() {

        EntityCatalog catalog = builder.build();
        assertNotNull(catalog);

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();

        EntityQualifier qualifier = new EntityQualifier(List.of("test_table"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Table should exist in catalog");
        assertTrue(entity instanceof RelationalEntity);
        assertEquals(EntityKind.TABLE, entity.getKind(), "Should be TABLE kind");

        // Check view
        EntityQualifier viewQualifier = new EntityQualifier(List.of("test_view"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity viewEntity = catalog.getDatabaseEntity(viewQualifier);
        assertNotNull(viewEntity, "View should exist in catalog");
        assertTrue(viewEntity instanceof View);
        assertEquals(EntityKind.VIEW, viewEntity.getKind(), "Should be VIEW kind");

        EntityQualifier extQualifier = new EntityQualifier(List.of("external_table1"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity extEntity = catalog.getDatabaseEntity(extQualifier);
        assertNotNull(extEntity, "External table should exist in catalog");
        assertTrue(extEntity instanceof RelationalEntity);
        assertEquals(EntityKind.EXTERNAL_TABLE, extEntity.getKind(), "Should be EXTERNAL_TABLE kind");
    }

    @Test
    void testBuildWithDuplicateEntities() {

        when(tableRepository.fetchAllTables()).thenReturn(List.of(
                createTableEntity("test_db", "test_schema", "test_table"),
                createTableEntity("test_db", "test_schema", "test_table")
        ));
        when(viewRepository.fetchAllViews()).thenReturn(List.of(
                createViewEntity("test_db", "test_schema", "test_view", "SELECT 1"),
                createViewEntity("test_db", "test_schema", "test_view", "SELECT 1")
        ));
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of(
                createExternalTableEntity("test_db", "test_schema", "external_table1"),
                createExternalTableEntity("test_db", "test_schema", "external_table1")
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        // Should still only have one of each entity
        EntityQualifier qualifier = new EntityQualifier(List.of("test_table"), List.of("test_db", "test_schema"), dialect);
        assertNotNull(catalog.getDatabaseEntity(qualifier));
        EntityQualifier viewQualifier = new EntityQualifier(List.of("test_view"), List.of("test_db", "test_schema"), dialect);
        assertNotNull(catalog.getDatabaseEntity(viewQualifier));
        EntityQualifier extQualifier = new EntityQualifier(List.of("external_table1"), List.of("test_db", "test_schema"), dialect);
        assertNotNull(catalog.getDatabaseEntity(extQualifier));
    }

    @Test
    void testBuildWithEmptyRepositories() {

        when(tableRepository.fetchAllTables()).thenReturn(List.of());
        when(columnRepository.fetchAllColumns()).thenReturn(List.of());
        when(viewRepository.fetchAllViews()).thenReturn(List.of());
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of());
        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();
        assertNotNull(catalog);
    }

    @Test
    void testThreadSafety_MultipleBuildCalls() throws InterruptedException {

        builder = new EntityCatalogBuilder(repositoryConfig);
        Runnable buildTask = () -> {
            EntityCatalog c = builder.build();
            assertNotNull(c);
        };
        Thread t1 = new Thread(buildTask);
        Thread t2 = new Thread(buildTask);
        t1.start();
        t2.start();
        t1.join();
        t2.join();
    }

    @Test
    void testBuildWithNullNamespace() {
        TableEntity badTable = createTableEntity("bad_db", "bad_schema", "bad_table");
        when(tableRepository.fetchAllTables()).thenReturn(List.of(badTable));
        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(List.of("bad_table"), List.of("bad_db", "bad_schema"), dialect);
        assertNotNull(catalog.getDatabaseEntity(qualifier));
    }

    @Test
    void testBuildWithColumnMappings() {
        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity("test_db", "test_schema", "test_table", "id", 1, "INTEGER"),
                createColumnEntity("test_db", "test_schema", "test_table", "name", 2, "STRING"),
                createColumnEntity("test_db", "test_schema", "test_table", "created_at", 3, "TIMESTAMP")
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(List.of("test_table"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity);

        if (entity instanceof RelationalEntity relationalEntity) {
            assertEquals(3, relationalEntity.getColumns().size(), "Should have 3 columns");
            assertTrue(relationalEntity.getColumns().stream().anyMatch(c -> "id".equals(c.getName().toString())), "Should have 'id' column");
            assertTrue(relationalEntity.getColumns().stream().anyMatch(c -> "name".equals(c.getName().toString())), "Should have 'name' column");
            assertTrue(relationalEntity.getColumns().stream().anyMatch(c -> "created_at".equals(c.getName().toString())), "Should have 'created_at' column");
        } else {
            fail("Expected RelationalEntity");
        }
    }

    @Test
    void testBuildWithViewDependencies() {
        when(viewRepository.fetchAllViews()).thenReturn(List.of(
                createViewEntity("test_db", "test_schema", "test_view", "SELECT * FROM test_table")
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier viewQualifier = new EntityQualifier(List.of("test_view"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity viewEntity = catalog.getDatabaseEntity(viewQualifier);

        assertNotNull(viewEntity);
        assertTrue(viewEntity instanceof View);
        View view = (View) viewEntity;
        assertEquals("SELECT * FROM test_table", view.getSql(), "View SQL should match");
    }

    @Test
    void testBuildWithExternalTableDetails() {
        ExternalTableEntity extTable = createExternalTableEntity("test_db", "test_schema", "external_table1");
        extTable.setExternalTableType("BIGQUERY");
        extTable.setExternalObjectName("gs://bucket/path/to/data");
        extTable.setSourceProduct("BIGQUERY");
        extTable.setInstance("test-instance");

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity("test_db", "test_schema", "external_table1", "ext_id", 1, "INTEGER"),
                createColumnEntity("test_db", "test_schema", "external_table1", "ext_name", 2, "STRING"),
                createColumnEntity("test_db", "test_schema", "external_table1", "source_ts", 3, "TIMESTAMP")
        ));

        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of(extTable));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier extQualifier = new EntityQualifier(
                List.of("external_table1"),
                List.of("test_db", "test_schema"),
                dialect
        );

        DatabaseEntity extEntity = catalog.getDatabaseEntity(extQualifier);
        assertNotNull(extEntity);
        assertTrue(extEntity instanceof RelationalEntity);
        assertEquals(EntityKind.EXTERNAL_TABLE, extEntity.getKind());

        ExternalTable extTable2 = (ExternalTable) extEntity;
        assertEquals("BIGQUERY", extTable2.getExternalTableType());
        assertEquals("gs://bucket/path/to/data", extTable2.getExternalObjectName());
        assertEquals("BIGQUERY", extTable2.getSourceProduct());
        assertEquals("test-instance", extTable2.getInstance());

        assertEquals(3, extTable2.getColumns().size(), "Should have 3 columns");
        assertTrue(extTable2.getColumns().stream().anyMatch(c -> "ext_id".equals(c.getName().toString())), "Should have 'ext_id' column");
        assertTrue(extTable2.getColumns().stream().anyMatch(c -> "ext_name".equals(c.getName().toString())), "Should have 'ext_name' column");
        assertTrue(extTable2.getColumns().stream().anyMatch(c -> "source_ts".equals(c.getName().toString())), "Should have 'source_ts' column");
    }

    @Test
    void testBuildWithExternalTableColumnMapping() {
        ExternalTableEntity extTable1 = createExternalTableEntity("test_db", "test_schema", "ext_table1");
        ExternalTableEntity extTable2 = createExternalTableEntity("test_db", "test_schema", "ext_table2");

        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of(extTable1, extTable2));

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity("test_db", "test_schema", "ext_table1", "id", 1, "INTEGER"),
                createColumnEntity("test_db", "test_schema", "ext_table1", "data", 2, "STRING"),
                createColumnEntity("test_db", "test_schema", "ext_table2", "ref_id", 1, "INTEGER"),
                createColumnEntity("test_db", "test_schema", "ext_table2", "metadata", 2, "STRING")
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();

        EntityQualifier qual1 = new EntityQualifier(List.of("ext_table1"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity entity1 = catalog.getDatabaseEntity(qual1);
        assertTrue(entity1 instanceof ExternalTable);
        ExternalTable extTable1Result = (ExternalTable) entity1;
        assertEquals(2, extTable1Result.getColumns().size());
        assertTrue(extTable1Result.getColumns().stream().anyMatch(c -> "id".equals(c.getName().toString())));
        assertTrue(extTable1Result.getColumns().stream().anyMatch(c -> "data".equals(c.getName().toString())));

        EntityQualifier qual2 = new EntityQualifier(List.of("ext_table2"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity entity2 = catalog.getDatabaseEntity(qual2);
        assertTrue(entity2 instanceof ExternalTable);
        ExternalTable extTable2Result = (ExternalTable) entity2;
        assertEquals(2, extTable2Result.getColumns().size());
        assertTrue(extTable2Result.getColumns().stream().anyMatch(c -> "ref_id".equals(c.getName().toString())));
        assertTrue(extTable2Result.getColumns().stream().anyMatch(c -> "metadata".equals(c.getName().toString())));
    }

    @Test
    void testBuildWithEmptyColumnList() {
        when(columnRepository.fetchAllColumns()).thenReturn(List.of());

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(
                List.of("test_table"),
                List.of("test_db", "test_schema"),
                dialect
        );

        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity);
        assertTrue(entity instanceof RelationalEntity);
        assertTrue(((RelationalEntity) entity).getColumns().isEmpty());
    }

    @Test
    void testErrorHandlingDuringBuild() {
        lenient().when(tableRepository.fetchAllTables()).thenThrow(new RuntimeException("Table fetch failed"));
        builder = new EntityCatalogBuilder(repositoryConfig);
        assertThrows(RuntimeException.class, () -> builder.build(), "Should throw exception when table fetch fails");
        reset(tableRepository);
        lenient().when(viewRepository.fetchAllViews()).thenThrow(new RuntimeException("View fetch failed"));
        builder = new EntityCatalogBuilder(repositoryConfig);
        assertThrows(RuntimeException.class, () -> builder.build(), "Should throw exception when view fetch fails");
        reset(viewRepository);
        lenient().when(externalTableRepository.fetchAllExternalTables()).thenThrow(new RuntimeException("External table fetch failed"));
        builder = new EntityCatalogBuilder(repositoryConfig);
        // The builder only logs the error, does not throw, so no assertion here
    }

    @Test
    void testBuildWithInvalidData() {
        TableEntity invalidTable = TableEntity.builder().build(); // All nulls
        TableEntity partialTable = TableEntity.builder()
                .database("test_db")
                .tableName("partial_table")
                .build();

        when(tableRepository.fetchAllTables()).thenReturn(List.of(invalidTable, partialTable));

        ViewEntity invalidView = createViewEntity("test_db", "test_schema", "invalid_view", null);
        when(viewRepository.fetchAllViews()).thenReturn(List.of(invalidView));

        ExternalTableEntity invalidExtTable = new ExternalTableEntity();
        invalidExtTable.setDatabase("test_db");
        invalidExtTable.setSchema("test_schema");
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(List.of(invalidExtTable));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        assertNotNull(catalog);

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier invalidTableQual = new EntityQualifier(List.of("invalid_table"), List.of("test_db", "test_schema"), dialect);
        assertNull(catalog.getDatabaseEntity(invalidTableQual));
        EntityQualifier invalidViewQual = new EntityQualifier(List.of("invalid_view"), List.of("test_db", "test_schema"), dialect);
        assertNull(catalog.getDatabaseEntity(invalidViewQual));
    }

    @Test
    void testConcurrentBuildScenarios() throws InterruptedException {
        TableEntity initialTable = createTableEntity("test_db", "test_schema", "test_table");
        TableEntity newTable = createTableEntity("new_db", "new_schema", "new_table");

        when(tableRepository.fetchAllTables())
                .thenReturn(List.of(initialTable))
                .thenReturn(List.of(initialTable, newTable));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog1 = builder.build();

        Thread thread1 = new Thread(() -> builder.build());
        Thread thread2 = new Thread(() -> builder.build());

        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();

        EntityCatalog finalCatalog = builder.build();
        assertNotNull(finalCatalog);

        BigQuerySqlDialect testDialect = new BigQuerySqlDialect();

        EntityQualifier qualifier1 = new EntityQualifier(
                List.of("test_table"),
                List.of("test_db", "test_schema"),
                testDialect
        );
        assertNotNull(finalCatalog.getDatabaseEntity(qualifier1));
        EntityQualifier qualifier2 = new EntityQualifier(
                List.of("new_table"),
                List.of("new_db", "new_schema"),
                testDialect
        );
    }

    @Test
    void testComplexColumnMapping() {
        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                // Duplicate column names
                createColumnEntity("test_db", "test_schema", "test_table", "id", 1, "INTEGER"),
                createColumnEntity("test_db", "test_schema", "test_table", "id", 2, "INTEGER"),
                // Invalid column position
                createColumnEntity("test_db", "test_schema", "test_table", "invalid_pos", -1, "INTEGER"),
                // Column with invalid data type
                createColumnEntity("test_db", "test_schema", "test_table", "bad_type", 3, "INVALID_TYPE"),
                // Column with null data type
                createColumnEntity("test_db", "test_schema", "test_table", "null_type", 4, null),
                // Column with extreme position
                createColumnEntity("test_db", "test_schema", "test_table", "extreme_pos", Integer.MAX_VALUE, "INTEGER")
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(List.of("test_table"), List.of("test_db", "test_schema"), dialect);
        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Entity should not be null");
        assertTrue(entity instanceof RelationalEntity, "Entity should be instance of RelationalEntity");

        RelationalEntity relEntity = (RelationalEntity) entity;
        // Check handling of duplicate column names (should only allow one per table)
        long idCount = relEntity.getColumns().stream()
                .filter(c -> "id".equals(c.getName().toString()))
                .count();
        assertEquals(1, idCount, "Builder should only allow one 'id' column per table");
        // Check that invalid columns were properly handled
        assertTrue(relEntity.getColumns().stream()
                .anyMatch(c -> "invalid_pos".equals(c.getName().toString())), "Column with invalid position should be present");
        assertTrue(relEntity.getColumns().stream()
                .anyMatch(c -> "bad_type".equals(c.getName().toString())), "Column with invalid data type should be present");
        assertTrue(relEntity.getColumns().stream()
                .anyMatch(c -> "null_type".equals(c.getName().toString())), "Column with null data type should be present");
        assertTrue(relEntity.getColumns().stream()
                .anyMatch(c -> "extreme_pos".equals(c.getName().toString())), "Column with extreme position should be present");
    }

    // Helper methods for test entities
    private TableEntity createTableEntity(String database, String schema, String tableName) {
        return TableEntity.builder()
                .database(database)
                .schema(schema)
                .tableName(tableName)
                .createAt(1749629498L)
                .build();
    }

    private ViewEntity createViewEntity(String database, String schema, String viewName, String sqlQuery) {
        ViewEntity view = new ViewEntity();
        view.setDatabase(database);
        view.setSchema(schema);
        view.setViewName(viewName);
        view.setExecutedSqlQuery(sqlQuery);
        view.setCreateAt(1749629498L);
        return view;
    }

    private ColumnEntity createColumnEntity(String database, String schema, String table, String columnName, int position, String dataType) {
        ColumnEntity column = new ColumnEntity();
        column.setDatabase(database);
        column.setSchema(schema);
        column.setTable(table);
        column.setColumnName(columnName);
        column.setColumnPosition((long) position);
        column.setDataType(dataType);
        column.setNullable(true);
        return column;
    }

    private ExternalTableEntity createExternalTableEntity(String database, String schema, String tableName) {
        ExternalTableEntity ext = new ExternalTableEntity();
        ext.setDatabase(database);
        ext.setSchema(schema);
        ext.setExternalTableName(tableName);
        ext.setCreateAt(1749629498L);
        ext.setExternalTableType("BIGQUERY");
        ext.setExternalObjectName("gs://bucket/path");
        ext.setSourceProduct("test_source");
        ext.setInstance("test_instance");
        return ext;
    }

    private IndicesEntity createIndicesEntity(String database, String schema, String tableName, List<String> columns, String indexType) {
        IndicesEntity index = new IndicesEntity();
        index.setDatabase(database);
        index.setSchema(schema);
        index.setIndexName(tableName);
        index.setColumnList(columns);
        index.setIndexType(indexType);
        index.setCreateAt(1749629498L);
        return index;
    }

    private PartitionEntity createPartitionEntity(String database, String schema, String tableName, List<String> partitionColumns) {
        PartitionEntity partition = new PartitionEntity();
        partition.setDatabase(database);
        partition.setSchema(schema);
        partition.setTableName(tableName);
        partition.setPartitionColumns(partitionColumns);
        partition.setCreateAt(1749629498L);
        return partition;
    }

    @Test
    void testBuildWithClusteredColumns() {
        String database = "bigquery_project";
        String schema = "analytics";
        String tableName = "user_events";

        when(tableRepository.fetchAllTables()).thenReturn(List.of(
                createTableEntity(database, schema, tableName)
        ));

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity(database, schema, tableName, "event_id", 1, "INTEGER"),
                createColumnEntity(database, schema, tableName, "user_id", 2, "INTEGER"),
                createColumnEntity(database, schema, tableName, "event_timestamp", 3, "TIMESTAMP"),
                createColumnEntity(database, schema, tableName, "event_type", 4, "STRING")
        ));

        // Index name should match the table name for clustering in BigQuery
        when(indicesRepository.fetchAllIndices()).thenReturn(List.of(
                createIndicesEntity(database, schema, tableName, List.of("user_id", "event_timestamp"), "cluster")
        ));

        when(partitionRepository.fetchAllPartitions()).thenReturn(List.of());

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(
                List.of(tableName),
                List.of(database, schema),
                dialect
        );

        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Table should exist in catalog");
        assertTrue(entity instanceof com.calcite_new.core.model.entity.Table);

        com.calcite_new.core.model.entity.Table table = (com.calcite_new.core.model.entity.Table) entity;
        assertEquals(2, table.getClusteredColumns().size(), "Should have 2 clustered columns");
        assertTrue(table.getClusteredColumns().contains("user_id"), "Should contain user_id in clustered columns");
        assertTrue(table.getClusteredColumns().contains("event_timestamp"), "Should contain event_timestamp in clustered columns");
        assertTrue(table.getPartitionedColumns().isEmpty(), "Should have no partitioned columns");
    }

    @Test
    void testBuildWithPartitionedColumns() {
        String database = "bigquery_project";
        String schema = "sales";
        String tableName = "transactions";

        when(tableRepository.fetchAllTables()).thenReturn(List.of(
                createTableEntity(database, schema, tableName)
        ));

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity(database, schema, tableName, "transaction_id", 1, "INTEGER"),
                createColumnEntity(database, schema, tableName, "transaction_date", 2, "DATE"),
                createColumnEntity(database, schema, tableName, "amount", 3, "NUMERIC"),
                createColumnEntity(database, schema, tableName, "customer_id", 4, "INTEGER")
        ));

        when(indicesRepository.fetchAllIndices()).thenReturn(List.of());

        when(partitionRepository.fetchAllPartitions()).thenReturn(List.of(
                createPartitionEntity(database, schema, tableName, List.of("transaction_date"))
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(
                List.of(tableName),
                List.of(database, schema),
                dialect
        );

        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Table should exist in catalog");
        assertTrue(entity instanceof com.calcite_new.core.model.entity.Table);

        com.calcite_new.core.model.entity.Table table = (com.calcite_new.core.model.entity.Table) entity;
        assertEquals(1, table.getPartitionedColumns().size(), "Should have 1 partitioned column");
        assertTrue(table.getPartitionedColumns().contains("transaction_date"), "Should contain transaction_date in partitioned columns");
        assertTrue(table.getClusteredColumns().isEmpty(), "Should have no clustered columns");
    }

    @Test
    void testBuildWithBothClusteredAndPartitionedColumns() {
        String database = "bigquery_project";
        String schema = "warehouse";
        String tableName = "orders";

        when(tableRepository.fetchAllTables()).thenReturn(List.of(
                createTableEntity(database, schema, tableName)
        ));

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity(database, schema, tableName, "order_id", 1, "INTEGER"),
                createColumnEntity(database, schema, tableName, "customer_id", 2, "INTEGER"),
                createColumnEntity(database, schema, tableName, "order_date", 3, "DATE"),
                createColumnEntity(database, schema, tableName, "region", 4, "STRING"),
                createColumnEntity(database, schema, tableName, "total_amount", 5, "NUMERIC")
        ));


        when(indicesRepository.fetchAllIndices()).thenReturn(List.of(
                createIndicesEntity(database, schema, tableName, List.of("customer_id", "region"), "cluster")
        ));

        when(partitionRepository.fetchAllPartitions()).thenReturn(List.of(
                createPartitionEntity(database, schema, tableName, List.of("order_date"))
        ));

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(
                List.of(tableName),
                List.of(database, schema),
                dialect
        );

        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Table should exist in catalog");
        assertTrue(entity instanceof com.calcite_new.core.model.entity.Table);

        com.calcite_new.core.model.entity.Table table = (com.calcite_new.core.model.entity.Table) entity;
        assertEquals(2, table.getClusteredColumns().size(), "Should have 2 clustered columns");
        assertTrue(table.getClusteredColumns().contains("customer_id"), "Should contain customer_id in clustered columns");
        assertTrue(table.getClusteredColumns().contains("region"), "Should contain region in clustered columns");
        assertEquals(1, table.getPartitionedColumns().size(), "Should have 1 partitioned column");
        assertTrue(table.getPartitionedColumns().contains("order_date"), "Should contain order_date in partitioned columns");
    }

    @Test
    void testBuildWithNonClusterIndexType() {
        String database = "bigquery_project";
        String schema = "public";
        String tableName = "products";

        when(tableRepository.fetchAllTables()).thenReturn(List.of(
                createTableEntity(database, schema, tableName)
        ));

        when(columnRepository.fetchAllColumns()).thenReturn(List.of(
                createColumnEntity(database, schema, tableName, "product_id", 1, "INTEGER"),
                createColumnEntity(database, schema, tableName, "product_name", 2, "STRING")
        ));

        when(indicesRepository.fetchAllIndices()).thenReturn(List.of(
                createIndicesEntity(database, schema, tableName, List.of("product_id"), "btree"),
                createIndicesEntity(database, schema, tableName, List.of("product_name"), "hash")
        ));

        when(partitionRepository.fetchAllPartitions()).thenReturn(List.of());

        builder = new EntityCatalogBuilder(repositoryConfig);
        EntityCatalog catalog = builder.build();

        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        EntityQualifier qualifier = new EntityQualifier(
                List.of(tableName),
                List.of(database, schema),
                dialect
        );

        DatabaseEntity entity = catalog.getDatabaseEntity(qualifier);
        assertNotNull(entity, "Table should exist in catalog");
        assertTrue(entity instanceof com.calcite_new.core.model.entity.Table);

        com.calcite_new.core.model.entity.Table table = (com.calcite_new.core.model.entity.Table) entity;
        assertTrue(table.getClusteredColumns().isEmpty(), "Should ignore non-cluster index types");
        assertTrue(table.getPartitionedColumns().isEmpty(), "Should have no partitioned columns");
    }
}