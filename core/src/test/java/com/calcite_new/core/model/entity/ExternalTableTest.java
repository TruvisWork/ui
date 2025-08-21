package com.calcite_new.core.model.entity;

import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExternalTableTest {

    @Test
    void testExternalTableConstruction() {
        // Setup
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        List<Identifier> namespace = Arrays.asList(
            Identifier.of("database", dialect),
            Identifier.of("schema", dialect)
        );
        Identifier name = Identifier.of("ext_table", dialect);
        List<Column> columns = Arrays.asList(
            new Column(Identifier.of("id", dialect), 0, DataType.create(SqlTypeName.INTEGER), false),
            new Column(Identifier.of("name", dialect), 1, DataType.create(SqlTypeName.VARCHAR, 50), true)
        );
        long createAt = System.currentTimeMillis();
        String externalTableType = "EXTERNAL_CSV";
        String externalObjectName = "gs://bucket/path/file.csv";
        String sourceProduct = "BIG_QUERY";
        String instance = "production";

        // Act
        ExternalTable table = new ExternalTable(
            namespace,
            name,
            columns,
            createAt,
            externalTableType,
            externalObjectName,
            sourceProduct,
            instance
        );

        // Assert
        assertEquals(namespace, table.getNamespace());
        assertEquals(name, table.getName());
        assertEquals(columns, table.getColumns());
        assertEquals(createAt, table.getCreatedTimestamp());
        assertEquals(externalTableType, table.getExternalTableType());
        assertEquals(externalObjectName, table.getExternalObjectName());
        assertEquals(sourceProduct, table.getSourceProduct());
        assertEquals(instance, table.getInstance());
        assertEquals(EntityKind.EXTERNAL_TABLE, table.getKind());
    }

    @Test
    void testExternalTableImplementsRelationalEntity() {
        // Verify ExternalTable implements RelationalEntity
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        ExternalTable table = new ExternalTable(
            Arrays.asList(Identifier.of("db", dialect), Identifier.of("schema", dialect)),
            Identifier.of("table", dialect),
            Arrays.asList(new Column(Identifier.of("col", dialect), 0, DataType.create(SqlTypeName.VARCHAR, 10), true)),
            System.currentTimeMillis(),
            "CSV",
            "file.csv",
            "BIG_QUERY",
            "dev"
        );

        assertTrue(table instanceof RelationalEntity);
        assertNotNull(table.getColumns());
    }

    @Test
    void testExternalTableValidation() {
        BigQuerySqlDialect dialect = new BigQuerySqlDialect();
        List<Identifier> namespace = Arrays.asList(
            Identifier.of("database", dialect),
            Identifier.of("schema", dialect)
        );
        Identifier name = Identifier.of("ext_table", dialect);
        List<Column> columns = Arrays.asList(
            new Column(Identifier.of("id", dialect), 0, DataType.create(SqlTypeName.INTEGER), false)
        );
        long createAt = System.currentTimeMillis();

        // Test null columns
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, null, createAt, "CSV", "file.csv", "BIG_QUERY", "dev"
        ));

        // Test null/empty externalTableType
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, null, "file.csv", "BIG_QUERY", "dev"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, "", "file.csv", "BIG_QUERY", "dev"
        ));

        // Test null/empty externalObjectName
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, "CSV", null, "BIG_QUERY", "dev"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, "CSV", "", "BIG_QUERY", "dev"
        ));

        // Test null/empty sourceProduct
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, "CSV", "file.csv", null, "dev"
        ));
        assertThrows(IllegalArgumentException.class, () -> new ExternalTable(
            namespace, name, columns, createAt, "CSV", "file.csv", "", "dev"
        ));

        // Test null instance is allowed
        assertDoesNotThrow(() -> new ExternalTable(
            namespace, name, columns, createAt, "CSV", "file.csv", "BIG_QUERY", null
        ));
    }
}
