package com.calcite_new.sql.relationextractor;

import com.calcite_new.core.config.RepositoryConfig;
import com.calcite_new.core.entity.ColumnEntity;
import com.calcite_new.core.entity.TableEntity;
import com.calcite_new.core.entity.ViewEntity;
import com.calcite_new.core.entity.ExternalTableEntity;
import com.calcite_new.core.repository.ColumnRepository;
import com.calcite_new.core.repository.TableRepository;
import com.calcite_new.core.repository.ViewRepository;
import com.calcite_new.core.repository.ExternalTableRepository;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.sql.relationextractor.EntityType;
import com.calcite_new.core.service.EntityCatalogBuilder;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.core.model.entity.ExternalTable;
import com.calcite_new.core.entity.ExternalTableEntity;
import com.calcite_new.core.model.Namespace;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.model.entity.EntityRelationship;
import com.calcite_new.sql.relationextractor.RelationshipType;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class RelationshipExtractorTest {
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(repositoryConfig.getTableRepo()).thenReturn(tableRepository);
        when(repositoryConfig.getViewRepo()).thenReturn(viewRepository);
        when(repositoryConfig.getColumnRepo()).thenReturn(columnRepository);
        when(repositoryConfig.getExternalTableRepo()).thenReturn(externalTableRepository);
        when(tableRepository.fetchAllTables()).thenReturn(Arrays.asList(createTableEntity("test_db", "test_schema", "test_table")));
        when(viewRepository.fetchAllViews()).thenReturn(Arrays.asList(createViewEntity("test_db", "test_schema", "test_view", "SELECT * FROM test_table")));
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(Arrays.asList(createExternalTableEntity("test_db", "test_schema", "external_table1")));
        dialect = new BigQuerySqlDialect();
        catalog = new EntityCatalogBuilder(repositoryConfig).build();
        entityResolver = new EntityResolver(catalog);
        relationshipExtractor = new RelationshipExtractor(entityResolver, dialect);
    }

    @Mock
    private RepositoryConfig repositoryConfig;

    @Mock
    private TableRepository tableRepository;

    @Mock
    private ViewRepository viewRepository;

    @Mock
    private ColumnRepository columnRepository;

    @Mock
    private ExternalTableRepository externalTableRepository;

    private RelationshipExtractor relationshipExtractor;
    private EntityCatalog catalog;
    private EntityResolver entityResolver;
    private BigQuerySqlDialect dialect;



    private EntityCatalog createTestCatalogWithHardcodedData(BigQuerySqlDialect dialect) {
        EntityCatalog catalog = new EntityCatalog();
        List<Identifier> namespace = List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect));
        // Add test view
        View view1 = new View(namespace, Identifier.of("test_view", dialect), List.of(), "SELECT * FROM test_table", System.currentTimeMillis());
        catalog.addEntity(view1);
        // Add external table
        List<Identifier> extNamespace = List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect));
        ExternalTable extTable = new ExternalTable(
            extNamespace,
            Identifier.of("external_table1", dialect),
            List.of(),
            System.currentTimeMillis(),
            "BIGQUERY",
            "gs://bucket/path",
            "test_source",
            "test_instance"
        );
        catalog.addEntity(extTable);
        return catalog;
    }

    private TableEntity createTableEntity(final String database, String schema, String tableName) {
        TableEntity table = new TableEntity();
        table.setDatabase(database);
        table.setSchema(schema);
        table.setTableName(tableName);
        table.setCreateAt(1749629498L);
        return table;
    }

    // Helper method for testCreateEntity_WithEmptyRepositories (if needed)
    // If you need to test with an empty extractor, instantiate it as in other tests.

    // Helper methods
    private ViewEntity createViewEntity(final String database, String schema, String viewName, String sqlQuery) {
        ViewEntity view = new ViewEntity();
        view.setDatabase(database);
        view.setSchema(schema);
        view.setViewName(viewName);
        view.setExecutedSqlQuery(sqlQuery);
        view.setCreateAt(1749629498L);
        return view;
    }

    private ExternalTableEntity createExternalTableEntity(final String database, String schema, String tableName) {
        ExternalTableEntity externalTable = new ExternalTableEntity();
        externalTable.setDatabase(database);
        externalTable.setSchema(schema);
        externalTable.setExternalTableName(tableName);
        externalTable.setCreateAt(1749629498L);
        externalTable.setExternalTableType("BIGQUERY");
        externalTable.setExternalObjectName("gs://bucket/path");
        externalTable.setSourceProduct("test_source");
        externalTable.setInstance("test_instance");
        return externalTable;
    }

    @Test
    void testCreateEntity_WithDuplicateEntities() {
        when(tableRepository.fetchAllTables()).thenReturn(Arrays.asList(
                createTableEntity("test_db", "test_schema", "dup_table"),
                createTableEntity("test_db", "test_schema", "dup_table")
        ));
        when(viewRepository.fetchAllViews()).thenReturn(Arrays.asList(
                createViewEntity("test_db", "test_schema", "dup_view", "SELECT 1"),
                createViewEntity("test_db", "test_schema", "dup_view", "SELECT 1")
        ));
        when(externalTableRepository.fetchAllExternalTables()).thenReturn(Arrays.asList(
                createExternalTableEntity("test_db", "test_schema", "dup_ext"),
                createExternalTableEntity("test_db", "test_schema", "dup_ext")
        ));
        EntityCatalog dupCatalog = new EntityCatalogBuilder(repositoryConfig).build();
        EntityResolver dupResolver = new EntityResolver(dupCatalog);
        RelationshipExtractor dupExtractor = new RelationshipExtractor(dupResolver, dialect);
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "dup_table"), SqlParserPos.ZERO);
        Entity entity = dupExtractor.createEntity(id, "test_db", "test_schema");
        assertNotNull(entity);
        assertEquals(EntityType.TABLE, entity.getEntityType());
        SqlIdentifier extId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "dup_ext"), SqlParserPos.ZERO);
        Entity extEntity = dupExtractor.createEntity(extId, "test_db", "test_schema");
        assertNotNull(extEntity);
        assertEquals(EntityType.EXTERNAL_TABLE, extEntity.getEntityType());
    }

    @Test
    void testCreateEntity_ErrorHandling() {
        // Should not throw, should default to TABLE
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("bad_db", "bad_schema", "bad_table"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(id, null, null);
        assertNotNull(entity);
        assertEquals(EntityType.TABLE, entity.getEntityType());
    }

    @Test
    void testCreateAccess() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new java.util.ArrayList<>();
        relationshipExtractor.createAccess(id, "user1", "test_db", "test_schema", rels);
        assertEquals(1, rels.size());
        assertEquals(RelationshipType.ACCESSES, rels.get(0).getRelationshipType());
        assertEquals("user1", rels.get(0).getSourceEntity().getEntityName());
        assertEquals("test_table", rels.get(0).getTargetEntity().getEntityName());
        assertEquals(EntityType.USER, rels.get(0).getSourceEntity().getEntityType());
        assertEquals(EntityType.TABLE, rels.get(0).getTargetEntity().getEntityType());
    }

    @Test
    void testCreateDependsOn() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        SqlIdentifier id2 = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table2"), SqlParserPos.ZERO);
        List<EntityRelationship> rels2 = new java.util.ArrayList<>();
        relationshipExtractor.createDependsOn(id, id2, "test_db", "test_schema", rels2);
        assertEquals(1, rels2.size());
        assertEquals(RelationshipType.DEPENDS_ON, rels2.get(0).getRelationshipType());
        assertEquals("test_table", rels2.get(0).getSourceEntity().getEntityName());
        assertEquals("test_table2", rels2.get(0).getTargetEntity().getEntityName());
    }
    
    @Test
    void testCreateEntity_WithFullQualifiedName() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        // Use a mock resolver for this test
        Table table = new Table(
            List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect)),
            Identifier.of("test_table", dialect),
            List.of(),
            System.currentTimeMillis()
        );
        EntityResolver mockResolver = mock(EntityResolver.class);
        when(mockResolver.resolve(any())).thenReturn(table);
        RelationshipExtractor extractor = new RelationshipExtractor(mockResolver, dialect);
        Entity entity = extractor.createEntity(id, "default_db", "default_schema");
        assertNotNull(entity);
        assertEquals("test_db", entity.getDatabase());
        assertEquals("test_schema", entity.getSchema());
        assertEquals("test_table", entity.getEntityName());
        assertEquals(EntityType.TABLE, entity.getEntityType());
    }
    
    @Test
    void testGetEntityType_Table() {
        SqlIdentifier tableId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(tableId, "test_db", "test_schema");
        assertEquals(EntityType.TABLE, entity.getEntityType());
    }
    
    @Test
    void testGetEntityType_View() {
        SqlIdentifier viewId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_view"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(viewId, "test_db", "test_schema");
        assertEquals(EntityType.VIEW, entity.getEntityType());
    }
    
    @Test
    void testGetEntityType_ExternalTable() {
        // Test with explicit external table
        SqlIdentifier extId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "external_table1"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(extId, "test_db", "test_schema");
        assertEquals(EntityType.EXTERNAL_TABLE, entity.getEntityType());
        assertEquals("test_db", entity.getDatabase());
        assertEquals("test_schema", entity.getSchema());
        assertEquals("external_table1", entity.getEntityName());
    }

    @Test
    void testExternalTable_Relationships() {
        // Test external table access relationship
        SqlIdentifier extId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "external_table1"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new ArrayList<>();
        relationshipExtractor.createAccess(extId, "user1", "test_db", "test_schema", rels);
        assertEquals(1, rels.size());
        EntityRelationship rel = rels.get(0);
        assertEquals(RelationshipType.ACCESSES, rel.getRelationshipType());
        assertEquals(EntityType.USER, rel.getSourceEntity().getEntityType());
        assertEquals("user1", rel.getSourceEntity().getEntityName());
        assertEquals(EntityType.EXTERNAL_TABLE, rel.getTargetEntity().getEntityType());
        assertEquals("external_table1", rel.getTargetEntity().getEntityName());
        
        // Test external table dependency relationship
        SqlIdentifier tableId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        List<EntityRelationship> depRels = new ArrayList<>();
        relationshipExtractor.createDependsOn(tableId, extId, "test_db", "test_schema", depRels);
        assertEquals(1, depRels.size());
        EntityRelationship depRel = depRels.get(0);
        assertEquals(RelationshipType.DEPENDS_ON, depRel.getRelationshipType());
        assertEquals(EntityType.TABLE, depRel.getSourceEntity().getEntityType());
        assertEquals("test_table", depRel.getSourceEntity().getEntityName());
        assertEquals(EntityType.EXTERNAL_TABLE, depRel.getTargetEntity().getEntityType());
        assertEquals("external_table1", depRel.getTargetEntity().getEntityName());
    }

    @Test
    void testExternalTable_DefaultDbSchema() {
        // Test with default database and schema
        SqlIdentifier shortExtId = new SqlIdentifier(Arrays.asList("external_table1"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(shortExtId, "test_db", "test_schema");
        assertEquals(EntityType.EXTERNAL_TABLE, entity.getEntityType());
        assertEquals("test_db", entity.getDatabase());
        assertEquals("test_schema", entity.getSchema());
        
        // Test with partial qualification
        SqlIdentifier partialExtId = new SqlIdentifier(Arrays.asList("test_schema", "external_table1"), SqlParserPos.ZERO);
        Entity partialEntity = relationshipExtractor.createEntity(partialExtId, "test_db", null);
        assertEquals(EntityType.EXTERNAL_TABLE, partialEntity.getEntityType());
        assertEquals("test_db", partialEntity.getDatabase());
        assertEquals("test_schema", partialEntity.getSchema());
    }

    @Test
    void testExternalTable_ComplexRelationships() {
        // Set up external tables and regular tables
        SqlIdentifier extId1 = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "external_table1"), SqlParserPos.ZERO);
        SqlIdentifier extId2 = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "external_table2"), SqlParserPos.ZERO);
        SqlIdentifier tableId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new ArrayList<>();

        // Mock resolver to return correct entities for each identifier
        Table table = new Table(
            List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect)),
            Identifier.of("test_table", dialect),
            List.of(),
            System.currentTimeMillis()
        );
        ExternalTable extTable1 = new ExternalTable(
            List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect)),
            Identifier.of("external_table1", dialect),
            List.of(),
            System.currentTimeMillis(),
            "BIGQUERY",
            "gs://bucket/path",
            "test_source",
            "test_instance"
        );
        ExternalTable extTable2 = new ExternalTable(
            List.of(Identifier.of("test_db", dialect), Identifier.of("test_schema", dialect)),
            Identifier.of("external_table2", dialect),
            List.of(),
            System.currentTimeMillis(),
            "BIGQUERY",
            "gs://bucket/path2",
            "test_source",
            "test_instance"
        );
        EntityResolver mockResolver = mock(EntityResolver.class);
        // Always return the correct entity for each identifier
        when(mockResolver.resolve(any())).thenAnswer(invocation -> {
            com.calcite_new.core.model.EntityQualifier q = invocation.getArgument(0);
            String name = q.getQualifiers().get(q.getQualifiers().size() - 1).getName();
            switch (name) {
                case "test_table": return table;
                case "external_table1": return extTable1;
                case "external_table2": return extTable2;
                default: return null;
            }
        });
        RelationshipExtractor extractor = new RelationshipExtractor(mockResolver, dialect);

        // Create a chain of dependencies: table -> ext1 -> ext2
        extractor.createDependsOn(tableId, extId1, "test_db", "test_schema", rels);
        extractor.createDependsOn(extId1, extId2, "test_db", "test_schema", rels);

        assertEquals(2, rels.size());
        // Check first relationship
        assertEquals(EntityType.TABLE, rels.get(0).getSourceEntity().getEntityType());
        assertEquals(EntityType.EXTERNAL_TABLE, rels.get(0).getTargetEntity().getEntityType());
        assertEquals("test_table", rels.get(0).getSourceEntity().getEntityName());
        assertEquals("external_table1", rels.get(0).getTargetEntity().getEntityName());
        // Check second relationship
        assertEquals(EntityType.EXTERNAL_TABLE, rels.get(1).getSourceEntity().getEntityType());
        assertEquals(EntityType.EXTERNAL_TABLE, rels.get(1).getTargetEntity().getEntityType());
        assertEquals("external_table1", rels.get(1).getSourceEntity().getEntityName());
        assertEquals("external_table2", rels.get(1).getTargetEntity().getEntityName());
    }

    @Test
    void testExternalTable_ErrorCases() {
        // Removed test with null components as SqlIdentifier does not allow nulls

        // Test with empty identifier using the same mock-based extractor
        SqlIdentifier emptyId = new SqlIdentifier(List.of(), SqlParserPos.ZERO);
        assertThrows(IllegalArgumentException.class, () -> {
            relationshipExtractor.createEntity(emptyId, "test_db", "test_schema");
        });

        // Test relationships with null entities
        List<EntityRelationship> rels = new ArrayList<>();
        try {
            relationshipExtractor.createAccess(null, "user1", "test_db", "test_schema", rels);
            fail("Expected NullPointerException for null identifier");
        } catch (NullPointerException e) {
            // Expected
        } catch (Exception e) {
            fail("Expected NullPointerException, got " + e.getClass().getSimpleName());
        }
    }
    
    @Test
    void testGetEntityType_NotFound() {
        SqlIdentifier notFoundId = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "nonexistent"), SqlParserPos.ZERO);
        Entity entity = relationshipExtractor.createEntity(notFoundId, "test_db", "test_schema");
        assertEquals(EntityType.TABLE, entity.getEntityType()); // Default to TABLE when not found
    }
    
    @Test
    void testCreateEntity_NullIdentifier() {
        assertThrows(IllegalArgumentException.class, () -> {
            relationshipExtractor.createEntity(null, "db", "schema");
        });
    }
    
    @Test
    void testCreateAccess_NullIdentifier() {
        List<EntityRelationship> rels = new java.util.ArrayList<>();
        assertThrows(NullPointerException.class, () -> {
            relationshipExtractor.createAccess(null, "user1", "db", "schema", rels);
        });
    }
    
    @Test
    void testCreateAccess_EmptyUserName() {
        List<EntityRelationship> rels = new java.util.ArrayList<>();
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("db", "schema", "table"), SqlParserPos.ZERO);
        
        assertThrows(IllegalArgumentException.class, () -> {
            relationshipExtractor.createAccess(id, "", "db", "schema", rels);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            relationshipExtractor.createAccess(id, "  ", "db", "schema", rels);
        });
    }
    
    @Test
    void testCreateAccess_NullRelations() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("db", "schema", "table"), SqlParserPos.ZERO);
        assertThrows(NullPointerException.class, () -> {
            relationshipExtractor.createAccess(id, "user1", "db", "schema", null);
        });
    }

    @Test
    void testCreateDependsOn_SelfDependency() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new ArrayList<>();
        
        relationshipExtractor.createDependsOn(id, id, "test_db", "test_schema", rels);
        assertTrue(rels.isEmpty(), "Self-dependency should be skipped");
    }
    
    @Test
    void testCreateDependsOn_NullIdentifiers() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("db", "schema", "table"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new java.util.ArrayList<>();
        
        assertThrows(NullPointerException.class, () -> {
            relationshipExtractor.createDependsOn(null, id, "db", "schema", rels);
        });
        
        assertThrows(NullPointerException.class, () -> {
            relationshipExtractor.createDependsOn(id, null, "db", "schema", rels);
        });
    }

    @Test
    void testCreateAccess_Deduplication() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        List<EntityRelationship> rels = new ArrayList<>();
        relationshipExtractor.createAccess(id, "user1", "test_db", "test_schema", rels);
        // Try to add the same relationship again
        relationshipExtractor.createAccess(id, "user1", "test_db", "test_schema", rels);
        assertEquals(1, rels.size(), "Duplicate access relationship should not be added");
    }

    @Test
    void testCreateDependsOn_Deduplication() {
        SqlIdentifier id = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table"), SqlParserPos.ZERO);
        SqlIdentifier id2 = new SqlIdentifier(Arrays.asList("test_db", "test_schema", "test_table2"), SqlParserPos.ZERO);
        List<EntityRelationship> rels2 = new ArrayList<>();
        relationshipExtractor.createDependsOn(id, id2, "test_db", "test_schema", rels2);
        // Try to add the same relationship again
        relationshipExtractor.createDependsOn(id, id2, "test_db", "test_schema", rels2);
        assertEquals(1, rels2.size(), "Duplicate depends-on relationship should not be added");
    }
}