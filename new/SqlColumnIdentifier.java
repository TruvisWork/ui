package com.calcite_new.sql;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;

import java.util.List;

/**
 * Custom SqlNode for column identifiers to distinguish them from table identifiers.
 * This allows the sql-core module to easily identify column references vs table references.
 */
public class SqlColumnIdentifier extends SqlIdentifier {
    private Column entity;
    private DatabaseEntity databaseEntity;

    public SqlColumnIdentifier(List<String> names, SqlParserPos pos) {
        super(names, pos);
        this.entity = null;
        this.databaseEntity = null;
    }

    /**
     * Creates a column identifier with a list of names and attached column entity
     */
    public SqlColumnIdentifier(List<String> names, SqlParserPos pos, Column entity) {
        super(names, pos);
        this.entity = entity;
        this.databaseEntity = null;
    }

    /**
     * Creates a column identifier with a list of names, attached column entity, and parent table entity
     */
    public SqlColumnIdentifier(List<String> names, SqlParserPos pos, Column entity, DatabaseEntity databaseEntity) {
        super(names, pos);
        this.entity = entity;
        this.databaseEntity = databaseEntity;
    }

    /**
     * Creates a column identifier with a single name
     */
    public SqlColumnIdentifier(String name, SqlParserPos pos) {
        super(java.util.Arrays.asList(name), pos);
        this.entity = null;
    }

    /**
     * Returns the attached column entity for this column
     */
    public Column getEntity() {
        return entity;
    }

    /**
     * Sets the attached column entity for this column
     */
    public void setEntity(Column entity) {
        this.entity = entity;
    }

    /**
     * Returns the parent table entity for this column
     */
    public DatabaseEntity getDatabaseEntity() {
        return databaseEntity;
    }

    /**
     * Sets the parent table entity for this column
     */
    public void setDatabaseEntity(DatabaseEntity databaseEntity) {
        this.databaseEntity = databaseEntity;
    }

    /**
     * Returns true to indicate this is a column identifier
     */
    public boolean isColumnIdentifier() {
        return true;
    }

    /**
     * Returns the column name (last part of the identifier)
     */
    public String getColumnName() {
        if (names != null && !names.isEmpty()) {
            return names.get(names.size() - 1);
        }
        return null;
    }

    /**
     * Returns the table name if present, null otherwise
     */
    public String getTableName() {
        if (names.size() >= 2) {
            return names.get(names.size() - 2);
        }
        return null;
    }

    /**
     * Returns the schema name if present, null otherwise
     */
    public String getSchemaName() {
        if (names.size() >= 3) {
            return names.get(names.size() - 3);
        }
        return null;
    }

    /**
     * Returns the database name if present, null otherwise
     */
    public String getDatabaseName() {
        if (names.size() >= 4) {
            return names.get(0);
        }
        return null;
    }

    /**
     * Returns true if this is a fully qualified column name (database.schema.table.column)
     */
    public boolean isFullyQualified() {
        return names.size() == 4;
    }

    /**
     * Returns true if this is a schema-qualified column name (schema.table.column)
     */
    public boolean isSchemaQualified() {
        return names.size() == 3;
    }

    /**
     * Returns true if this is a table-qualified column name (table.column)
     */
    public boolean isTableQualified() {
        return names.size() == 2;
    }

    /**
     * Returns true if this is a simple column name (column)
     */
    public boolean isSimple() {
        return names.size() == 1;
    }

}
