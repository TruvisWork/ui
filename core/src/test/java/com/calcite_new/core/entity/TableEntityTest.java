package com.calcite_new.core.entity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.calcite_new.core.config.HibernateUtil;
import org.hibernate.Transaction;

public class TableEntityTest {
    @Mock
    private SessionFactory sessionFactory;
    @Mock
    private Session session;
    @Mock
    private Query<TableEntity> tableQuery;
    @Mock
    private Query<ColumnEntity> columnQuery;
    @Mock
    private Query<ViewEntity> viewQuery;
    @Mock
    private Query<FunctionEntity> functionQuery;
    @Mock
    private Query<ProcedureEntity> procedureQuery;
    @Mock
    private Query<MacroEntity> macroQuery;
    @Mock
    private Query<ExternalTableEntity> externalTableQuery;
    @Mock
    private Query<IndicesEntity> indicesQuery;
    @Mock
    private Query<PartitionEntity> partitionQuery;
    @Mock
    private Query<QueryLog> queryLogQuery;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(sessionFactory.openSession()).thenReturn(session);
        when(session.createQuery("FROM TableEntity", TableEntity.class)).thenReturn(tableQuery);
        when(session.createQuery("FROM ColumnEntity", ColumnEntity.class)).thenReturn(columnQuery);
        when(session.createQuery("FROM ViewEntity", ViewEntity.class)).thenReturn(viewQuery);
        when(session.createQuery("FROM FunctionEntity", FunctionEntity.class)).thenReturn(functionQuery);
        when(session.createQuery("FROM ProcedureEntity", ProcedureEntity.class)).thenReturn(procedureQuery);
        when(session.createQuery("FROM MacroEntity", MacroEntity.class)).thenReturn(macroQuery);
        when(session.createQuery("FROM ExternalTableEntity", ExternalTableEntity.class)).thenReturn(externalTableQuery);
        when(session.createQuery("FROM IndicesEntity", IndicesEntity.class)).thenReturn(indicesQuery);
        when(session.createQuery("FROM PartitionEntity", PartitionEntity.class)).thenReturn(partitionQuery);
        when(session.createQuery("FROM QueryLog", QueryLog.class)).thenReturn(queryLogQuery);
    }

    @Test
    void testFetchAllTables() {
        TableEntity table1 = new TableEntity();
        table1.setTableName("users");
        table1.setSourceProduct("ProductA");
        TableEntity table2 = new TableEntity();
        table2.setTableName("orders");
        table2.setSourceProduct("ProductB");
        List<TableEntity> tables = Arrays.asList(table1, table2);
        when(tableQuery.getResultList()).thenReturn(tables);
        session.beginTransaction();
        List<TableEntity> result = session.createQuery("FROM TableEntity", TableEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("users", result.get(0).getTableName());
        assertEquals("ProductA", result.get(0).getSourceProduct());
        assertEquals("orders", result.get(1).getTableName());
        assertEquals("ProductB", result.get(1).getSourceProduct());
        session.close();
    }

    @Test
    void testFetchAllColumns() {
        ColumnEntity col1 = new ColumnEntity();
        col1.setColumnName("id");
        col1.setDataType("int");
        ColumnEntity col2 = new ColumnEntity();
        col2.setColumnName("username");
        col2.setDataType("varchar");
        List<ColumnEntity> columns = Arrays.asList(col1, col2);
        when(columnQuery.getResultList()).thenReturn(columns);
        session.beginTransaction();
        List<ColumnEntity> result = session.createQuery("FROM ColumnEntity", ColumnEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("id", result.get(0).getColumnName());
        assertEquals("int", result.get(0).getDataType());
        assertEquals("username", result.get(1).getColumnName());
        assertEquals("varchar", result.get(1).getDataType());
        session.close();
    }

    @Test
    void testFetchAllViews() {
        ViewEntity v1 = new ViewEntity();
        v1.setViewName("active_users_view");
        v1.setViewType("materialized");
        when(viewQuery.getResultList()).thenReturn(Arrays.asList(v1));
        session.beginTransaction();
        List<ViewEntity> result = session.createQuery("FROM ViewEntity", ViewEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("active_users_view", result.get(0).getViewName());
        assertEquals("materialized", result.get(0).getViewType());
        session.close();
    }

    @Test
    void testFetchAllFunctions() {
        FunctionEntity f1 = new FunctionEntity();
        f1.setFunctionName("calculate_discount");
        f1.setSourceProduct("ProductF");
        when(functionQuery.getResultList()).thenReturn(Arrays.asList(f1));
        session.beginTransaction();
        List<FunctionEntity> result = session.createQuery("FROM FunctionEntity", FunctionEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("calculate_discount", result.get(0).getFunctionName());
        assertEquals("ProductF", result.get(0).getSourceProduct());
        session.close();
    }

    @Test
    void testFetchAllProcedures() {
        ProcedureEntity p1 = new ProcedureEntity();
        p1.setProcedureName("archive_orders");
        p1.setUserName("admin");
        when(procedureQuery.getResultList()).thenReturn(Arrays.asList(p1));
        session.beginTransaction();
        List<ProcedureEntity> result = session.createQuery("FROM ProcedureEntity", ProcedureEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("archive_orders", result.get(0).getProcedureName());
        assertEquals("admin", result.get(0).getUserName());
        session.close();
    }

    @Test
    void testFetchAllMacros() {
        MacroEntity m1 = new MacroEntity();
        m1.setMacroName("macro_test");
        m1.setUserName("macro_user");
        when(macroQuery.getResultList()).thenReturn(Arrays.asList(m1));
        session.beginTransaction();
        List<MacroEntity> result = session.createQuery("FROM MacroEntity", MacroEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("macro_test", result.get(0).getMacroName());
        assertEquals("macro_user", result.get(0).getUserName());
        session.close();
    }

    @Test
    void testFetchAllExternalTables() {
        ExternalTableEntity e1 = new ExternalTableEntity();
        e1.setExternalTableName("external_data");
        e1.setExternalTableType("csv");
        when(externalTableQuery.getResultList()).thenReturn(Arrays.asList(e1));
        session.beginTransaction();
        List<ExternalTableEntity> result = session.createQuery("FROM ExternalTableEntity", ExternalTableEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("external_data", result.get(0).getExternalTableName());
        assertEquals("csv", result.get(0).getExternalTableType());
        session.close();
    }

    @Test
    void testFetchAllIndices() {
        IndicesEntity i1 = new IndicesEntity();
        i1.setIndexName("idx_user_id");
        i1.setIndexType("btree");
        when(indicesQuery.getResultList()).thenReturn(Arrays.asList(i1));
        session.beginTransaction();
        List<IndicesEntity> result = session.createQuery("FROM IndicesEntity", IndicesEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("idx_user_id", result.get(0).getIndexName());
        assertEquals("btree", result.get(0).getIndexType());
        session.close();
    }

    @Test
    void testFetchAllPartitions() {
        PartitionEntity p1 = new PartitionEntity();
        p1.setPartitionName("partition_2025");
        p1.setTableName("users");
        when(partitionQuery.getResultList()).thenReturn(Arrays.asList(p1));
        session.beginTransaction();
        List<PartitionEntity> result = session.createQuery("FROM PartitionEntity", PartitionEntity.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("partition_2025", result.get(0).getPartitionName());
        assertEquals("users", result.get(0).getTableName());
        session.close();
    }

    @Test
    void testFetchAllQueryLogs() {
        QueryLog q1 = new QueryLog();
        q1.setLogId("log123");
        q1.setSqlQuery("SELECT * FROM users");
        when(queryLogQuery.getResultList()).thenReturn(Arrays.asList(q1));
        session.beginTransaction();
        List<QueryLog> result = session.createQuery("FROM QueryLog", QueryLog.class).getResultList();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("log123", result.get(0).getLogId());
        assertEquals("SELECT * FROM users", result.get(0).getSqlQuery());
        session.close();
    }

    @Test
    void integrationTestFetchTableFromDatabase() {
        // Use a table_name that exists in your DB
        String testTableName = "table_e";

        Session session = HibernateUtil.getSessionFactory().openSession();
        Transaction tx = session.beginTransaction();
        TableEntity entity = session.get(TableEntity.class, testTableName);
        tx.commit();
        session.close();

        if (entity == null) {
            fail("No TableEntity found for table_name='" + testTableName + "'. Please insert a test row in your database.");
        } else {
            assertNotNull(entity.getTableName(), "Fetched entity from DB should have a table name");
            assertNotNull(entity.getSourceProduct(), "Fetched entity from DB should have a source product");
            assertEquals(testTableName, entity.getTableName());
            // Example: assertEquals("ProductA", entity.getSourceProduct());
        }
    }
}
