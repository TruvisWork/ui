package com.calcite_new.sql.core.processor.visitor.expander;

import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.sql.SqlColumnIdentifier;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.core.processor.visitor.scope.ScopeManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Handles wildcard expansion in SELECT statements.
 * Expands * and table.* patterns into individual column references.
 */
@Slf4j
public class WildcardExpander {

    private final ScopeManager scopeManager;

    public WildcardExpander(ScopeManager scopeManager) {
        this.scopeManager = Objects.requireNonNull(scopeManager, "scopeManager cannot be null");
    }


    /**
     * Expands a wildcard (*) to a list of individual column references.
     */
    public List<SqlNode> expandWildcardToList(SqlIdentifier wildcard) {
        if (wildcard == null || !wildcard.isStar()) {
            return List.of(wildcard);
        }

        try {
            List<SqlNode> expandedColumns = new ArrayList<>();
            Set<SqlIdentifier> availableTables = scopeManager.getAvailableTables();

            if (wildcard.names.size() == 1) {
                // Unqualified * - expand all columns from all available tables
                for (SqlIdentifier table : availableTables) {
                    List<SqlNode> tableColumns = getTableColumns(table);
                    expandedColumns.addAll(tableColumns);
                }
            } else {
                // Qualified table.* - expand columns from specific table
                String tableName = wildcard.names.get(0);
                SqlIdentifier targetTable = scopeManager.getTableByAlias(tableName);
                if (targetTable == null) {
                    targetTable = scopeManager.getTableByName(tableName);
                }

                if (targetTable != null) {
                    List<SqlNode> tableColumns = getTableColumns(targetTable);
                    expandedColumns.addAll(tableColumns);
                } else {
                    log.warn("Could not find table '{}' for wildcard expansion", tableName);
                    return List.of(wildcard); // Return original if table not found
                }
            }

            if (expandedColumns.isEmpty()) {
                log.warn("No columns found for wildcard expansion");
                return List.of(wildcard); // Return original if no columns found
            }

            log.debug("Expanded wildcard {} to {} columns", wildcard, expandedColumns.size());
            return expandedColumns;

        } catch (Exception e) {
            log.warn("Failed to expand wildcard {}: {}", wildcard, e.getMessage());
            return List.of(wildcard); // Return original on failure
        }
    }

    /**
     * Expands a wildcard with an alias to a list of column references.
     */
    public List<SqlNode> expandWildcardWithAliasToList(SqlIdentifier wildcard, SqlNode aliasNode) {
        if (aliasNode instanceof SqlIdentifier aliasId) {
            String alias;
            if (aliasId.names.size() == 1) {
                alias = aliasId.getSimple();
            } else {
                alias = aliasId.names.get(aliasId.names.size() - 1);
            }
            if (alias != null) {
                // For now, expand the wildcard and return the columns
                // In a full implementation, you might want to create aliased column references
                return expandWildcardToList(wildcard);
            }
        }
        return expandWildcardToList(wildcard);
    }


    /**
     * Gets all columns from a table as qualified SqlColumnIdentifier objects.
     */
    public List<SqlNode> getTableColumns(SqlIdentifier table) {
        List<SqlNode> columns = new ArrayList<>();

        if (table == null) {
            return columns;
        }

        try {
            DatabaseEntity entity = getEntity(table);
            if (entity instanceof Table tableEntity) {
                String tableName = getTableName(table);
                if (tableName != null) {
                    for (Column column : tableEntity.getColumns()) {
                        List<String> columnNames = getColumnNames(table, column, tableName);

                        SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                columnNames,
                                table.getParserPosition(),
                                column,
                                entity
                        );

                        columns.add(columnId);
                    }
                }
            } else {
                log.debug("Table entity is not a Table type, cannot expand columns");
            }
        } catch (Exception e) {
            log.warn("Failed to get columns for table {}: {}", table, e.getMessage());
        }

        return columns;
    }

    private static @NotNull List<String> getColumnNames(SqlIdentifier table, Column column, String tableName) {
        String columnName = column.getName().getName();

        // Create qualified column identifier
        List<String> columnNames = new ArrayList<>();
        if (table.names.size() > 1) {
            // Include table qualification
            columnNames.addAll(table.names);
        } else {
            columnNames.add(tableName);
        }
        columnNames.add(columnName);
        return columnNames;
    }


    /**
     * Helper method to extract table/view name from SqlIdentifier.
     */
    private String getTableName(SqlIdentifier identifier) {
        if (identifier == null) {
            return null;
        }

        if (identifier instanceof SqlTableIdentifier tableId) {
            return tableId.getTableName();
        } else if (identifier instanceof SqlViewIdentifier viewId) {
            return viewId.getViewName();
        } else if (identifier.names != null && !identifier.names.isEmpty()) {
            return identifier.names.get(identifier.names.size() - 1);
        }
        return null;
    }

    /**
     * Helper method to extract entity from SqlIdentifier.
     */
    private DatabaseEntity getEntity(SqlIdentifier identifier) {
        if (identifier instanceof SqlTableIdentifier tableId) {
            return tableId.getEntity();
        } else if (identifier instanceof SqlViewIdentifier viewId) {
            return viewId.getEntity();
        }
        return null;
    }
}
