package com.calcite_new.sql.core.processor.visitor.scope;

import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;
import lombok.Getter;
import org.apache.calcite.sql.SqlSelect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents information about a subquery and its column projections.
 * Used for resolving column references from subquery aliases.
 */
public class SubqueryInfo {

    @Getter
    private final SqlSelect subquery;

    @Getter
    private final List<ColumnProjection> columnProjections;

    private final Map<String, ColumnProjection> columnMap = new HashMap<>();

    public SubqueryInfo(SqlSelect subquery, List<ColumnProjection> columnProjections) {
        this.subquery = validateNotNull(subquery, "subquery");
        this.columnProjections = validateNotNull(columnProjections, "columnProjections");

        // Build column map for quick lookup
        for (ColumnProjection projection : columnProjections) {
            if (projection != null && projection.getColumnName() != null) {
                columnMap.put(projection.getColumnName().toLowerCase(), projection);
            }
        }
    }

    /**
     * Gets a column projection by column name.
     */
    public ColumnProjection getColumnProjection(String columnName) {
        return columnName != null ? columnMap.get(columnName.toLowerCase()) : null;
    }

    /**
     * Checks if a column exists in this subquery.
     */
    public boolean hasColumn(String columnName) {
        return columnName != null && columnMap.containsKey(columnName.toLowerCase());
    }

    /**
     * Validates that an object is not null and returns it.
     */
    private static <T> T validateNotNull(T object, String paramName) {
        if (object == null) {
            throw new IllegalArgumentException(paramName + " cannot be null");
        }
        return object;
    }

    /**
     * Represents a column projection in a subquery.
     */
    @Getter
    public static class ColumnProjection {
        private final String columnName;
        private final org.apache.calcite.sql.SqlNode expression;
        private final DatabaseEntity sourceEntity;
        private final Column sourceColumn;

        public ColumnProjection(String columnName, org.apache.calcite.sql.SqlNode expression, DatabaseEntity sourceEntity, Column sourceColumn) {
            this.columnName = validateNotNull(columnName, "columnName");
            this.expression = validateNotNull(expression, "expression");
            this.sourceEntity = sourceEntity; // Can be null
            this.sourceColumn = sourceColumn; // Can be null
        }

        /**
         * Validates that an object is not null and returns it.
         */
        private static <T> T validateNotNull(T object, String paramName) {
            if (object == null) {
                throw new IllegalArgumentException(paramName + " cannot be null");
            }
            return object;
        }
    }
}
