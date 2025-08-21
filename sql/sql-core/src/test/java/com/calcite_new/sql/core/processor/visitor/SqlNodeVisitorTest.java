package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.parser.BigQuerySqlParser;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import com.calcite_new.sql.relationextractor.RelationshipType;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.resolver.EntityResolver;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SqlNodeVisitorTest {

    private SqlNodeVisitor visitor;
    private static final String USER = "testUser";
    private static final String DATABASE = "testDb";
    private static final String SCHEMA = "foodmart";

    @BeforeEach
    void setUp() {
        EntityCatalog entityCatalog = new FoodmartCatalogBuilder()
                .withProjectName(DATABASE)
                .withDatasetName(SCHEMA)
                .build();
        EntityResolver entityResolver = new EntityResolver(entityCatalog);
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        RelationshipExtractor relationshipExtractor = new RelationshipExtractor(entityResolver, dialect);
        visitor = new SqlNodeVisitor(USER, DATABASE, SCHEMA, relationshipExtractor, entityCatalog);
    }

    @Test
    void testJoinOnStringColumn() throws Exception {
        String sql = "SELECT a.store_id, b.store_cost FROM foodmart.sales_fact_1997 a JOIN foodmart.sales_fact_1998 b ON a.product_id = b.product_id";
        SqlNode sqlNode = parseSql(sql);

        SqlNodeVisitor.Result result = sqlNode.accept(visitor);

        assertNotNull(result);
        assertEquals(StatementType.SELECT, result.getStatementType());
        result.getEntityRelationships().forEach(relationship -> {
            assertEquals(RelationshipType.ACCESSES, relationship.getRelationshipType(),
                    "All relationships in SELECT should be of type ACCESSES");
            assertEquals(USER, relationship.getSourceEntity().getEntityName());
            assertTrue(relationship.getTargetEntity().getEntityName().matches("^(sales_fact_1997|sales_fact_1998)$"),
                    "Target entity should be either sales_fact_1997 or sales_fact_1998");
        });
    }

    @Test
    void testInsertStatement() throws Exception {
        String sql = "INSERT INTO target_table (col1, col2) SELECT col1, col2 FROM source_table";
        SqlNode sqlNode = parseSql(sql);

        SqlNodeVisitor.Result result = sqlNode.accept(visitor);

        assertNotNull(result);
        assertEquals(StatementType.INSERT, result.getStatementType());
    }

    @Test
    void testDeleteStatement() throws Exception {
        String sql = "DELETE FROM table1 WHERE TRUE";
        SqlNode sqlNode = parseSql(sql);

        SqlNodeVisitor.Result result = sqlNode.accept(visitor);

        assertNotNull(result);
        assertEquals(StatementType.DELETE, result.getStatementType());

    }
    /**
     * <b>Update statement Test-Cases</b>
     * @throws Exception
     */
    @Test
    void testUpdateStatementMultipleTable() throws Exception {
        String sql = "UPDATE foodmart.sales_fact_1997 SET store_sales = 100.00 WHERE store_id IN (SELECT s1.store_id FROM foodmart.sales_fact_1998 s1 JOIN foodmart.store s2 ON s1.store_id = s2.store_id WHERE s2.store_type = 'Supermarket')";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertEquals(StatementType.UPDATE, result.getStatementType());
        assertEquals(5, result.getEntityRelationships().size());

        assertEquals(2, result.getSourceTables().size());
        assertTrue(result.getEntityRelationships().stream()
                .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.ACCESSES));
        assertEquals(2, result.getEntityRelationships().stream()
                .filter(relationship -> relationship.getRelationshipType() == RelationshipType.DEPENDS_ON)
                .count());

    }

    @Test
    void testUpdateStatementSingleTable() throws Exception {
        String sql = "UPDATE users SET status = 'active' WHERE id = 101";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertEquals(StatementType.UPDATE, result.getStatementType());
        // Always expect one source table in update
        assertEquals(0, result.getSourceTables().size());
        // Always expected one relationship in update
        assertEquals(1, result.getEntityRelationships().size());

        assertTrue(result.getEntityRelationships().stream()
                .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.ACCESSES));

        assertFalse(
             result.getEntityRelationships().stream()
              .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.DEPENDS_ON));

    }

    @Test
    void testUpdateStatementSelfJoin() throws Exception {
        String sql =
                "UPDATE employees e1 SET manager_id = NULL WHERE EXISTS ( SELECT 1 FROM employees e2  WHERE e1.id = e2.manager_id  AND e2.status = 'terminated')";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertEquals(StatementType.UPDATE, result.getStatementType());
        assertEquals(1, result.getSourceTables().size());
        assertEquals(2, result.getEntityRelationships().size());
        assertTrue(result.getEntityRelationships().stream()
                .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.ACCESSES));

        assertTrue(
                result.getEntityRelationships().stream()
                        .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.DEPENDS_ON));
    }

    @Test
    void testUpdateTableFromAnotherTable() throws Exception {
        String sql =
                "UPDATE employees e1 SET manager_id = NULL WHERE EXISTS ( SELECT 1 FROM temployees e2  WHERE e1.id = e2.manager_id  AND e2.status = 'terminated')";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertEquals(StatementType.UPDATE, result.getStatementType());
        assertEquals(1, result.getSourceTables().size());
        assertEquals(3, result.getEntityRelationships().size());
        assertTrue(result.getEntityRelationships().stream()
                .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.ACCESSES));

        assertTrue(
                result.getEntityRelationships().stream()
                        .anyMatch(relationship -> relationship.getRelationshipType() == RelationshipType.DEPENDS_ON));
    }


    @Test
    void testNullCall() {
        SqlNodeVisitor.Result result = visitor.visit((SqlCall) null);

        assertNotNull(result);
        assertTrue(result.getEntityRelationships().isEmpty());
        assertTrue(result.getSourceTables().isEmpty());
    }

    @Test
    void testIdentifierVisit() {
        SqlIdentifier identifier = new SqlIdentifier("columnName", SqlParserPos.ZERO);
        SqlNodeVisitor.Result result = visitor.visit(identifier);

        assertNotNull(result);
        assertTrue(result.getEntityRelationships().isEmpty());
        assertTrue(result.getSourceTables().isEmpty());
    }

    @Test
    void testMergeResults() {
        SqlNodeVisitor.Result main = new SqlNodeVisitor.Result();
        SqlNodeVisitor.Result other = new SqlNodeVisitor.Result();

        other.getSourceTables().add(new SqlIdentifier("table1", SqlParserPos.ZERO));

        SqlNodeVisitor.mergeResults(main, other);

        assertEquals(1, main.getSourceTables().size());
    }

    @Test
    void testOrderByInFromSubquerySetsFlag() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM table1 ORDER BY id) t";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertNotNull(result.getContext().getOrderByClause(), "OrderByClause should not be null if ORDER BY is in subquery");
        assertTrue(result.getContext().getOrderByClause().getIsOderByInsideSubQuery(), "isInsideSubQuery flag should be true when ORDER BY is in subquery");
    }

    @Test
    void testOrderByInNestedSubquerySetsFlag() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM (SELECT id FROM table1 ORDER BY id) t1) t2";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertNotNull(result.getContext().getOrderByClause(), "OrderByClause should not be null if ORDER BY is in nested subquery");
        assertTrue(result.getContext().getOrderByClause().getIsOderByInsideSubQuery(), "isInsideSubQuery flag should be true when ORDER BY is in nested subquery");
    }

    @Test
    void testOrderByInBothOuterAndInnerSubqueries() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM (SELECT id FROM table1 ORDER BY id) t1 ORDER BY id) t2";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertNotNull(result.getContext().getOrderByClause(), "OrderByClause should not be null if ORDER BY is in subqueries");
        assertTrue(result.getContext().getOrderByClause().getIsOderByInsideSubQuery(), "isInsideSubQuery flag should be true when ORDER BY is in subquery");
    }

    @Test
    void testOrderByOnlyInOuterSubquery() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM (SELECT id FROM table1) t1 ORDER BY id) t2";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        assertNotNull(result.getContext().getOrderByClause(), "OrderByClause should not be null if ORDER BY is in outer subquery");
        assertTrue(result.getContext().getOrderByClause().getIsOderByInsideSubQuery(), "isInsideSubQuery flag should be true when ORDER BY is in subquery");
    }

    @Test
    void testNoOrderByInSubqueries() throws Exception {
        String sql = "SELECT * FROM (SELECT id FROM (SELECT id FROM table1) t1) t2";
        SqlNode sqlNode = parseSql(sql);
        SqlNodeVisitor.Result result = sqlNode.accept(visitor);
        assertNotNull(result);
        // Should be null or false if there is no ORDER BY
        assertTrue(result.getContext().getOrderByClause() == null || !result.getContext().getOrderByClause().getIsOderByInsideSubQuery(), "OrderByClause should be null or flag false if no ORDER BY in subquery");
    }

    private SqlNode parseSql(String sql) throws Exception {
        BigQuerySqlParser parser = new BigQuerySqlParser();
        return parser.parse(sql);
    }
}