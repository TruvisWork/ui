package com.calcite_new.sql;

import com.calcite_new.core.model.entity.DatabaseEntity;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.List;

/**
 * Custom SqlNode for table identifiers to distinguish them from column identifiers.
 * This allows the sql-core module to easily identify table references vs column references.
 */
public class SqlTableIdentifier extends SqlIdentifier {

    private DatabaseEntity entity;

    /**
     * Creates a table identifier with a list of names (e.g., [database, schema, table])
     */
    public SqlTableIdentifier(List<String> names, SqlParserPos pos) {
        super(names, pos);
    }

    /**
     * Creates a table identifier with a list of names and associated entity
     */
    public SqlTableIdentifier(List<String> names, SqlParserPos pos, DatabaseEntity entity) {
        super(names, pos);
        this.entity = entity;
    }

    /**
     * Creates a table identifier with a single name
     */
    public SqlTableIdentifier(String name, SqlParserPos pos) {
        super(java.util.Arrays.asList(name), pos);
    }

    /**
     * Returns true to indicate this is a table identifier
     */
    public boolean isTableIdentifier() {
        return true;
    }

    /**
     * Returns the table name (last part of the identifier)
     */
    public String getTableName() {
        if (names != null && !names.isEmpty()) {
            return names.reverse().get(0);
        }
        return null;
    }

    /**
     * Returns the schema name if present, null otherwise
     */
    public String getSchemaName() {
        if (names.size() >= 2) {
            return names.reverse().get(1);
        }
        return null;
    }

    /**
     * Returns the database name if present, null otherwise
     */
    public String getDatabaseName() {
        if (names.size() >= 3) {
            return names.reverse().get(2);
        }
        return null;
    }

    /**
     * Returns true if this is a fully qualified table name (database.schema.table)
     */
    public boolean isFullyQualified() {
        return names.size() == 4;
    }

    /**
     * Returns true if this is a schema-qualified table name (schema.table)
     */
    public boolean isSchemaQualified() {
        return names.size() == 2;
    }

    /**
     * Returns true if this is a simple table name (table)
     */
    public boolean isSimple() {
        return names.size() == 1;
    }

    /**
     * Gets the associated database entity
     */
    public DatabaseEntity getEntity() {
        return entity;
    }

    /**
     * Sets the associated database entity
     */
    public void setEntity(DatabaseEntity entity) {
        this.entity = entity;
    }
}
