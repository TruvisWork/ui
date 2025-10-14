package com.calcite_new.sql;

import com.calcite_new.core.model.entity.DatabaseEntity;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.List;

/**
 * Custom SqlNode for view identifiers to distinguish them from table identifiers.
 * This allows the sql-core module to easily identify view references vs table references.
 */
public class SqlViewIdentifier extends SqlIdentifier {

    private DatabaseEntity entity;

    /**
     * Creates a view identifier with a list of names (e.g., [database, schema, view])
     */
    public SqlViewIdentifier(List<String> names, SqlParserPos pos) {
        super(names, pos);
    }

    /**
     * Creates a view identifier with a list of names and associated entity
     */
    public SqlViewIdentifier(List<String> names, SqlParserPos pos, DatabaseEntity entity) {
        super(names, pos);
        this.entity = entity;
    }

    /**
     * Creates a view identifier with a single name
     */
    public SqlViewIdentifier(String name, SqlParserPos pos) {
        super(java.util.Arrays.asList(name), pos);
    }

    /**
     * Returns true to indicate this is a view identifier
     */
    public boolean isViewIdentifier() {
        return true;
    }

    /**
     * Returns the view name (last part of the identifier)
     */
    public String getViewName() {
        if (names != null && !names.isEmpty()) {
            return names.get(names.size() - 1);
        }
        return null;
    }

    /**
     * Returns the schema name if present, null otherwise
     */
    public String getSchemaName() {
        if (names.size() >= 2) {
            return names.get(names.size() - 2);
        }
        return null;
    }

    /**
     * Returns true if this is a fully qualified view name (database.schema.view)
     */
    public boolean isFullyQualified() {
        return names.size() == 4;
    }

    /**
     * Returns true if this is a simple view name (view)
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
