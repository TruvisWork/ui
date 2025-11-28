package com.calcite_new.sql.core.processor.visitor.expander;

import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.sql.SqlColumnIdentifier;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.core.processor.visitor.scope.ScopeManager;
import com.calcite_new.sql.core.processor.visitor.scope.SubqueryInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
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
        if (wildcard == null) {
            return List.of(wildcard);
        }

        // Check if it's a wildcard - either unqualified (*) or qualified (table.*)
        boolean isQualifiedWildcard = wildcard.names.size() == 2 && wildcard.names.get(1).equals("*");
        if (!wildcard.isStar() && !isQualifiedWildcard) {
            return List.of(wildcard);
        }

        try {
            List<SqlNode> expandedColumns = new ArrayList<>();
            Set<SqlIdentifier> availableTables = scopeManager.getAvailableTables();

            if (wildcard.names.size() == 1) {
                // Unqualified * - first check if any available table is a CTE/subquery
                // Prioritize subqueries as they typically represent the FROM clause
                java.util.List<SubqueryInfo> foundSubqueries = new java.util.ArrayList<>();
                for (SqlIdentifier table : availableTables) {
                    String tableName = table.names != null && !table.names.isEmpty() ? table.names.get(table.names.size() - 1) : null;
                    if (tableName != null && scopeManager.hasSubquery(tableName)) {
                        SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                        if (cteInfo != null) {
                            foundSubqueries.add(cteInfo);
                        }
                    }
                }

                // If we found subqueries, expand to their column projections
                if (!foundSubqueries.isEmpty()) {
                    // If there's only one subquery, expand to its columns
                    // If there are multiple, expand to all of them (combine columns)
                    for (SubqueryInfo subqueryInfo : foundSubqueries) {
                        for (SubqueryInfo.ColumnProjection proj : subqueryInfo.getColumnProjections()) {
                            SqlNode expr = proj.getExpression();

                            // If the expression is a simple SqlIdentifier, convert it to SqlColumnIdentifier with entity info
                            if (expr instanceof SqlIdentifier id && !(expr instanceof SqlColumnIdentifier)) {
                                SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                        id.names,
                                        id.getParserPosition(),
                                        proj.getSourceColumn(),
                                        proj.getSourceEntity()
                                );
                                expandedColumns.add(columnId);
                                String colName = columnId.names != null && !columnId.names.isEmpty() ? columnId.names.get(columnId.names.size() - 1) : "?";
                            } else {
                                // For SqlColumnIdentifier or complex expressions, add as-is
                                expandedColumns.add(expr);
                            }
                        }
                    }
                    if (!expandedColumns.isEmpty()) {
                        return expandedColumns;
                    }
                }

                // Fall back to expanding all columns from all available tables
                for (SqlIdentifier table : availableTables) {
                    List<SqlNode> tableColumns = getTableColumns(table);
                    expandedColumns.addAll(tableColumns);
                }
            } else {
                // Qualified table.* - expand columns from specific table
                String tableName = wildcard.names.get(0);

                // First check if it's a CTE
                if (scopeManager.hasSubquery(tableName)) {
                    SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                    if (cteInfo != null) {
                        log.debug("Expanding qualified wildcard from CTE: {}", tableName);
                        for (SubqueryInfo.ColumnProjection proj : cteInfo.getColumnProjections()) {
                            SqlNode expr = proj.getExpression();

                            // If the expression is a simple SqlIdentifier, convert it to SqlColumnIdentifier with entity info
                            if (expr instanceof SqlIdentifier id && !(expr instanceof SqlColumnIdentifier)) {
                                SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                        id.names,
                                        id.getParserPosition(),
                                        proj.getSourceColumn(),
                                        proj.getSourceEntity()
                                );
                                expandedColumns.add(columnId);
                                String colName = id.names != null && !id.names.isEmpty() ? id.names.get(id.names.size() - 1) : "?";
                            } else {
                                // For SqlColumnIdentifier or complex expressions, add as-is
                                expandedColumns.add(expr);
                            }
                            log.debug("Added CTE column: {} with entity: {}", proj.getColumnName(), proj.getSourceEntity());
                        }
                        return expandedColumns;
                    }
                }

                SqlIdentifier targetTable = scopeManager.getTableByAlias(tableName);
                if (targetTable == null) {
                    targetTable = scopeManager.getTableByName(tableName);
                }

                if (targetTable != null) {
                    List<SqlNode> tableColumns = getTableColumns(targetTable);
                    expandedColumns.addAll(tableColumns);
                } else {
                    // Try subquery/CTE alias expansion (e.g., UNNEST alias) - duplicate check but with println
                    if (scopeManager.hasSubquery(tableName)) {
                        SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                        if (cteInfo != null) {
                            for (SubqueryInfo.ColumnProjection proj : cteInfo.getColumnProjections()) {
                                SqlNode expr = proj.getExpression();
                                if (expr instanceof SqlIdentifier id && !(expr instanceof SqlColumnIdentifier)) {
                                    SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                            id.names,
                                            id.getParserPosition(),
                                            proj.getSourceColumn(),
                                            proj.getSourceEntity()
                                    );
                                    expandedColumns.add(columnId);
                                } else {
                                    expandedColumns.add(expr);
                                }
                            }
                        }
                    } else {
                        // Try learned nested alias binding (e.g., rules -> owner=rules_audit, baseToken=workflow)
                        DatabaseEntity owner = scopeManager.getNestedOwner(tableName);
                        String baseToken = scopeManager.getNestedBaseToken(tableName);
                        if (owner != null && baseToken != null) {
                            try {
                                // Get dialect - create BigQuery dialect as default
                                com.calcite_new.core.dialect.sql.SqlDialect dialect = new com.calcite_new.core.dialect.sql.BigQuerySqlDialect();

                                // Find the baseToken column entity (e.g., "workflow" column) from the owner table
                                com.calcite_new.core.model.entity.Column baseColumnEntity = null;
                                if (owner instanceof Table tableOwner) {
                                    baseColumnEntity = tableOwner.getColumns().stream()
                                            .filter(col -> col.getName().getName().equalsIgnoreCase(baseToken))
                                            .findFirst()
                                            .orElse(null);
                                } else if (owner instanceof View viewOwner) {
                                    baseColumnEntity = viewOwner.getColumns().stream()
                                            .filter(col -> col.getName().getName().equalsIgnoreCase(baseToken))
                                            .findFirst()
                                            .orElse(null);
                                }

                                // If we found the base column (e.g., workflow), use it as the source entity
                                // For rules.*, we'll just expand to the base column (workflow) since we don't know
                                // the exact nested structure. This allows MODIFIES relationships to be created
                                // from the workflow column to target columns.
                                if (baseColumnEntity != null) {
                                    // Build qualified table names for the owner
                                    java.util.List<String> qualifiedTableNames = new java.util.ArrayList<>();
                                    if (owner.getNamespace() != null) {
                                        for (com.calcite_new.core.model.Identifier ns : owner.getNamespace()) {
                                            qualifiedTableNames.add(ns.getNormalizedName());
                                        }
                                    }
                                    qualifiedTableNames.add(owner.getName().getName());

                                    // Just use the base token (workflow) itself, not expanded nested fields
                                    java.util.List<String> names = new java.util.ArrayList<>(qualifiedTableNames);
                                    names.add(baseToken);

                                    // Use the base column entity (workflow) as the attached entity
                                    SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                            names,
                                            wildcard.getParserPosition(),
                                            baseColumnEntity,  // Use workflow column entity as fallback
                                            owner              // Use owner table as database entity
                                    );
                                    expandedColumns.add(columnId);
                                } else {
                                    // Fallback: create a synthetic column if baseToken column not found in catalog
                                    String nestedPath = baseToken + "." + tableName;
                                    java.util.List<String> qualifiedTableNames = new java.util.ArrayList<>();
                                    if (owner.getNamespace() != null) {
                                        for (com.calcite_new.core.model.Identifier ns : owner.getNamespace()) {
                                            qualifiedTableNames.add(ns.getNormalizedName());
                                        }
                                    }
                                    qualifiedTableNames.add(owner.getName().getName());
                                    java.util.List<String> names = new java.util.ArrayList<>(qualifiedTableNames);
                                    names.add(nestedPath);

                                    com.calcite_new.core.model.entity.Column syntheticColumn = com.calcite_new.core.model.entity.Column.builder()
                                            .namespace(owner.getNamespace())
                                            .name(com.calcite_new.core.model.Identifier.of(nestedPath, dialect))
                                            .dialect(dialect)
                                            .ordinalPosition(0)
                                            .type(com.calcite_new.core.model.entity.DataType.create(org.apache.calcite.sql.type.SqlTypeName.VARCHAR))
                                            .build();

                                    SqlColumnIdentifier columnId = new SqlColumnIdentifier(
                                            names,
                                            wildcard.getParserPosition(),
                                            syntheticColumn,
                                            owner
                                    );
                                    expandedColumns.add(columnId);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to synthesize nested column for wildcard {}: {}", wildcard, e.getMessage(), e);
                            }
                        } else {
                            log.warn("Could not find table '{}' for wildcard expansion", tableName);
                            return List.of(wildcard); // Return original if table not found
                        }
                    }
                }
            }

            if (expandedColumns.isEmpty()) {
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
            String alias = aliasId.getSimple();
            if (alias != null) {
                // For now, expand the wildcard and return the columns
                // In a full implementation, you might want to create aliased column references
                return expandWildcardToList(wildcard);
            }
        }
        return expandWildcardToList(wildcard);
    }

    /**
     * Expands a wildcard with EXCEPT to a list of column references, excluding specified columns.
     */
    public List<SqlNode> expandWildcardWithExceptToList(SqlIdentifier wildcard, SqlNodeList exceptList) {
        if (exceptList == null) {
            return expandWildcardToList(wildcard);
        }

        // Collect column names to exclude
        Set<String> excludeColumns = new java.util.HashSet<>();
        java.util.List<String> exceptHints = new java.util.ArrayList<>(); // Use as hints for nested fields
        for (SqlNode exceptNode : exceptList) {
            if (exceptNode instanceof SqlIdentifier exceptId) {
                String exceptName = exceptId.getSimple();
                excludeColumns.add(exceptName);
                exceptHints.add(exceptName); // These are known nested fields
            }
        }

        // Handle the wildcard - check if it's a qualified wildcard (table.*)
        boolean isQualifiedWildcard = wildcard.names.size() == 2 && wildcard.names.get(1).equals("*");
        if (!wildcard.isStar() && !isQualifiedWildcard) {
            log.warn("expandWildcardWithExceptToList called with non-wildcard: {}", wildcard);
            return List.of(wildcard);
        }

        // Expand the wildcard
        List<SqlNode> expandedColumns = expandWildcardToList(wildcard);

        // Filter out excluded columns
        List<SqlNode> filteredColumns = new ArrayList<>();
        for (SqlNode column : expandedColumns) {
            // Skip wildcards in the expanded list - they couldn't be expanded
            if (column instanceof SqlIdentifier columnId && !(column instanceof SqlColumnIdentifier)) {
                // Check for both unqualified (*) and qualified (table.*) wildcards
                boolean isWildcard = columnId.isStar() ||
                        (columnId.names != null && columnId.names.size() == 2 && columnId.names.get(1).equals("*"));
                if (isWildcard) {
                    // This is still a wildcard, add it without filtering
                    filteredColumns.add(column);
                    continue;
                }
                // For regular SqlIdentifier, use getSimple() only if names.size() == 1
                String columnName = null;
                if (columnId.names != null) {
                    if (columnId.names.size() == 1) {
                        columnName = columnId.getSimple();
                    } else if (!columnId.names.isEmpty()) {
                        // For multi-part identifiers, use the last component
                        columnName = columnId.names.get(columnId.names.size() - 1);
                    }
                }
                if (columnName != null) {
                    // For nested paths like "workflow.name", extract just "name"
                    String finalFieldName = extractFinalFieldName(columnName);
                    if (!excludeColumns.contains(finalFieldName)) {
                        filteredColumns.add(column);
                    }
                } else {
                    // If we can't extract a name, include it anyway
                    filteredColumns.add(column);
                }
            } else if (column instanceof SqlColumnIdentifier columnId) {
                // For SqlColumnIdentifier, use getColumnName() to get the last component
                String columnName = columnId.getColumnName();
                if (columnName != null) {
                    // For nested paths like "workflow.name", extract just "name"
                    String finalFieldName = extractFinalFieldName(columnName);
                    if (!excludeColumns.contains(finalFieldName)) {
                        filteredColumns.add(column);
                    }
                } else {
                    // If we can't extract a name, include it anyway
                    filteredColumns.add(column);
                }
            } else {
                filteredColumns.add(column);
            }
        }

        log.debug("Expanded wildcard {} EXCEPT {} to {} columns",
                wildcard, excludeColumns, filteredColumns.size());
        return filteredColumns.isEmpty() ? List.of(wildcard) : filteredColumns;
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
            } else if (entity instanceof View viewEntity) {
                String tableName = getTableName(table);
                if (tableName != null) {
                    for (Column column : viewEntity.getColumns()) {
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
                log.debug("Table entity is not a Table/View type, cannot expand columns");
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

    /**
     * Extracts the final field name from a nested path.
     * For example: "workflow.name" -> "name", "name" -> "name"
     */
    private String extractFinalFieldName(String columnName) {
        if (columnName == null) {
            return null;
        }
        // If the column name contains a dot (nested path), extract the last part
        int lastDotIndex = columnName.lastIndexOf('.');
        if (lastDotIndex >= 0 && lastDotIndex < columnName.length() - 1) {
            return columnName.substring(lastDotIndex + 1);
        }
        return columnName;
    }
}
