package com.calcite_new.sql.core.processor.visitor.qualifier;

import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.SqlColumnIdentifier;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.core.processor.visitor.SqlComputedColumnIdentifier;
import com.calcite_new.sql.core.processor.DefaultQualifiers;
import com.calcite_new.sql.core.processor.visitor.scope.ScopeManager;
import com.calcite_new.sql.core.processor.visitor.scope.SubqueryInfo;
import com.calcite_new.sql.core.processor.visitor.scope.ComputedColumnInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles qualification of column identifiers.
 * Resolves unqualified column references using table aliases and scope information.
 */
@Slf4j
public class ColumnQualifier {
    private final DefaultQualifiers defaultQualifiers;
    private final SqlDialect dialect;
    private final EntityResolver entityResolver;
    private final ScopeManager scopeManager;

    public ColumnQualifier(DefaultQualifiers defaultQualifiers,
                           SqlDialect dialect,
                           EntityResolver entityResolver,
                           ScopeManager scopeManager) {
        this.defaultQualifiers = Objects.requireNonNull(defaultQualifiers, "defaultQualifiers cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.entityResolver = Objects.requireNonNull(entityResolver, "entityResolver cannot be null");
        this.scopeManager = Objects.requireNonNull(scopeManager, "scopeManager cannot be null");
    }

    /**
     * Enhanced column identifier qualification with comprehensive context resolution.
     */
    public SqlIdentifier qualifyColumnIdentifier(SqlColumnIdentifier columnId) {
        if (columnId == null) {
            return null;
        }

        try {
            String columnName = columnId.getColumnName();
            if (columnName == null) {
                return columnId;
            }

            if (columnId.names.size() == 1) {
                // Simple column reference - try to resolve from available tables
                if (scopeManager.hasComputedColumn(columnName)) {
                    ComputedColumnInfo columnInfo = scopeManager.getComputedColumn(columnName);
                    log.debug("Found {} reference: {} -> {} (type: {})",
                            columnInfo.isSimpleColumnAlias() ? "column alias" : "computed column",
                            columnName,
                            columnInfo.getExpression(),
                            columnInfo.getExpressionType());

                    return SqlComputedColumnIdentifier.from(columnInfo, columnName, columnId.getParserPosition());
                }

                return resolveSimpleColumnReference(columnName, columnId.getParserPosition());
            }

            // Extract table context from the column identifier
            List<String> tableContext = new ArrayList<>();
            for (int i = 0; i < columnId.names.size() - 1; i++) {
                tableContext.add(columnId.names.get(i));
            }

            if (tableContext.size() == 1) {
                String tableOrAlias = tableContext.get(0);

                if (scopeManager.hasComputedColumn(columnName)) {
                    ComputedColumnInfo columnInfo = scopeManager.getComputedColumn(columnName);
                    log.debug("Found {} reference with qualifier: {}.{} -> {} (type: {})",
                            columnInfo.isSimpleColumnAlias() ? "column alias" : "computed column",
                            tableOrAlias, columnName,
                            columnInfo.getExpression(),
                            columnInfo.getExpressionType());

                    return SqlComputedColumnIdentifier.from(columnInfo, columnId.names, columnId.getParserPosition());
                }

                if (scopeManager.hasSubquery(tableOrAlias)) {
                    SubqueryInfo subqueryInfo = scopeManager.getSubqueryInfo(tableOrAlias);
                    return resolveColumnFromSubquery(tableOrAlias, columnName, columnId.getParserPosition(), subqueryInfo);
                }

                // Check if it's a tracked table/view alias
                SqlIdentifier aliasedTable = scopeManager.getTableByAlias(tableOrAlias);
                if (aliasedTable != null) {
                    log.debug("Found aliased table/view for {} -> {}", tableOrAlias, getTableName(aliasedTable));
                    DatabaseEntity tableEntity = getEntity(aliasedTable);
                    Column columnEntity = createColumnEntity(tableEntity, columnName);
                    return new SqlColumnIdentifier(
                            Arrays.asList(tableOrAlias, columnName),
                            columnId.getParserPosition(),
                            columnEntity,
                            tableEntity
                    );
                }

                // Check if it's an unaliased table/view name
                SqlIdentifier directTable = scopeManager.getTableByName(tableOrAlias);
                if (directTable != null) {
                    DatabaseEntity tableEntity = getEntity(directTable);
                    Column columnEntity = createColumnEntity(tableEntity, columnName);
                    return new SqlColumnIdentifier(
                            Arrays.asList(tableOrAlias, columnName),
                            columnId.getParserPosition(),
                            columnEntity,
                            tableEntity
                    );
                }

                // Try to qualify the table name and resolve
                return qualifyColumnWithTableName(tableOrAlias, columnName, columnId.getParserPosition());
            }

            // Handle multi-part table context (schema.table.column or database.schema.table.column)
            return qualifyColumnWithQualifiedTable(tableContext, columnName, columnId.getParserPosition());

        } catch (Exception e) {
            log.warn("Failed to qualify column identifier {}: {}", columnId, e.getMessage());
        }

        return columnId;
    }

    /**
     * Resolves a column within a given qualified table/view identifier.
     */
    private SqlColumnIdentifier resolveColumnInTable(String columnName, SqlIdentifier tableIdentifier, SqlParserPos pos) {
        DatabaseEntity tableEntity = getEntity(tableIdentifier);
        if (tableEntity == null) {
            log.warn("Table entity not found for {}. Cannot resolve column {}", getTableName(tableIdentifier), columnName);
            return new SqlColumnIdentifier(Arrays.asList(getTableName(tableIdentifier), columnName), pos);
        }

        // Create a default column entity (matching original logic)
        Column columnEntity = createColumnEntity(tableEntity, columnName);
        List<String> qualifiedColumnNames = new ArrayList<>(tableIdentifier.names);
        qualifiedColumnNames.add(columnName);
        return new SqlColumnIdentifier(qualifiedColumnNames, pos, columnEntity, tableEntity);
    }

    /**
     * Resolves a simple column reference by checking all available tables/views and subqueries.
     */
    private SqlIdentifier resolveSimpleColumnReference(String columnName, SqlParserPos pos) {
        Set<SqlIdentifier> availableTables = scopeManager.getAvailableTables();

        if (availableTables.size() == 1) {
            // Only one table/view available, so the column must belong to it
            SqlIdentifier table = availableTables.iterator().next();
            return resolveColumnInTable(columnName, table, pos);
        }

        // Multiple tables/views available - try to find which table/view has this column
        List<SqlIdentifier> matchingTables = findTablesWithColumn(availableTables, columnName);

        if (matchingTables.size() == 1) {
            // Unique match found
            SqlIdentifier table = matchingTables.get(0);
            return resolveColumnInTable(columnName, table, pos);
        }

        if (matchingTables.size() > 1) {
            SqlIdentifier preferredTable = matchingTables.get(0);
            if (preferredTable != null) {
                return resolveColumnInTable(columnName, preferredTable, pos);
            }

            log.warn("Ambiguous column reference '{}' - found in multiple tables/views: {}",
                    columnName,
                    matchingTables.stream()
                            .map(table -> getTableName(table) != null ? getTableName(table) : "unknown")
                            .collect(Collectors.joining(", ")));
        }

        // If no tables available, check if we have CTEs/subqueries that might contain this column
        if (availableTables.isEmpty()) {
            // Try to resolve from CTEs/subqueries
            for (String subqueryAlias : scopeManager.getSubqueryAliases()) {
                SubqueryInfo subqueryInfo = scopeManager.getSubqueryInfo(subqueryAlias);
                if (subqueryInfo != null && subqueryInfo.hasColumn(columnName)) {
                    return resolveColumnFromSubquery(subqueryAlias, columnName, pos, subqueryInfo);
                }
            }
        }

        // Return as simple column if we can't resolve uniquely
        return new SqlColumnIdentifier(columnName, pos);
    }

    /**
     * Finds tables that contain the specified column.
     */
    private List<SqlIdentifier> findTablesWithColumn(Set<SqlIdentifier> availableTables, String columnName) {
        List<SqlIdentifier> matchingTables = new ArrayList<>();
        for (SqlIdentifier table : availableTables) {
            if (tableHasColumn(table, columnName)) {
                matchingTables.add(table);
            }
        }
        return matchingTables;
    }

    /**
     * Checks if a table/view has a specific column.
     */
    private boolean tableHasColumn(SqlIdentifier table, String columnName) {
        if (table == null || columnName == null) {
            return false;
        }

        DatabaseEntity entity = getEntity(table);
        if (entity instanceof Table tableEntity) {
            return tableEntity.getColumns().stream()
                    .anyMatch(col -> col.getName().getNormalizedName().equalsIgnoreCase(columnName));
        }

        // If we can't determine, assume the column exists to avoid breaking queries
        return true;
    }

    /**
     * Qualifies a column with a single table name context.
     */
    private SqlIdentifier qualifyColumnWithTableName(String tableName, String columnName, SqlParserPos pos) {
        try {
            // Try to qualify the table name first
            List<String> qualifiedTableNames = qualifyTableName(tableName);
            @SuppressWarnings("unused")
            DatabaseEntity tableEntity = resolveEntity(qualifiedTableNames);

            // Create fully qualified column identifier
            List<String> qualifiedColumnNames = new ArrayList<>(qualifiedTableNames);
            qualifiedColumnNames.add(columnName);

            return new SqlColumnIdentifier(
                    qualifiedColumnNames,
                    pos
            );
        } catch (Exception e) {
            log.debug("Failed to qualify column with table name {}.{}: {}", tableName, columnName, e.getMessage());
            // If qualification fails, return with just table.column
            return new SqlColumnIdentifier(
                    Arrays.asList(tableName, columnName),
                    pos
            );
        }
    }

    /**
     * Qualifies a column with multi-part table context.
     */
    private SqlIdentifier qualifyColumnWithQualifiedTable(List<String> tableContext, String columnName, SqlParserPos pos) {
        try {
            List<String> qualifiedTableNames;

            if (tableContext.size() == 3) {
                // Already fully qualified: database.schema.table
                qualifiedTableNames = new ArrayList<>(tableContext);
            } else {
                // Need to qualify using default qualifiers
                qualifiedTableNames = qualifyTableName(String.join(".", tableContext));
            }

            DatabaseEntity tableEntity = resolveEntity(qualifiedTableNames);

            List<String> qualifiedColumnNames = new ArrayList<>(qualifiedTableNames);
            qualifiedColumnNames.add(columnName);

            // Try to get the actual column entity from the table
            Optional<Column> columnEntity = Optional.empty();
            if (tableEntity instanceof Table table) {
                columnEntity = table.getColumns().stream()
                        .filter(col -> col.getName().getNormalizedName().equalsIgnoreCase(columnName))
                        .findFirst();
            } else if (tableEntity instanceof View view) {
                columnEntity = view.getColumns().stream()
                        .filter(col -> col.getName().getNormalizedName().equalsIgnoreCase(columnName))
                        .findFirst();
            }

            if (columnEntity.isPresent()) {
                return new SqlColumnIdentifier(qualifiedColumnNames, pos, columnEntity.get(), tableEntity);
            } else {
                // Column not found in table, create a default one
                Column defaultColumn = createColumnEntity(tableEntity, columnName);
                return new SqlColumnIdentifier(qualifiedColumnNames, pos, defaultColumn, tableEntity);
            }

        } catch (Exception e) {
            log.debug("Failed to qualify column with qualified table {}.{}: {}",
                    String.join(".", tableContext), columnName, e.getMessage());
            // If qualification fails, return with original context
            List<String> columnNames = new ArrayList<>(tableContext);
            columnNames.add(columnName);
            return new SqlColumnIdentifier(columnNames, pos);
        }
    }

    /**
     * Resolves a column reference from a subquery alias.
     */
    private SqlIdentifier resolveColumnFromSubquery(String alias, String columnName, SqlParserPos pos, SubqueryInfo subqueryInfo) {
        if (subqueryInfo == null) {
            log.warn("SubqueryInfo is null for alias '{}'", alias);
            return new SqlColumnIdentifier(Arrays.asList(alias, columnName), pos);
        }

        SubqueryInfo.ColumnProjection projection = subqueryInfo.getColumnProjection(columnName);
        if (projection == null) {
            log.warn("Column '{}' not found in subquery/CTE alias '{}'", columnName, alias);
            return new SqlColumnIdentifier(Arrays.asList(alias, columnName), pos);
        }

        // Get the actual source column and table entity
        Column sourceColumn = projection.getSourceColumn();
        DatabaseEntity parentTableEntity = projection.getSourceEntity();

        log.debug("Resolving CTE column {}.{}: sourceColumn={}, sourceEntity={}", 
                  alias, columnName, 
                  sourceColumn != null ? sourceColumn.getName() : "null",
                  parentTableEntity != null ? parentTableEntity.getName() : "null");

        // Always use the source table information, not the CTE/subquery alias
        if (sourceColumn != null && parentTableEntity != null) {
            // Build the qualified column name from the source table, not the CTE alias
            List<String> qualifiedNames = new ArrayList<>();

            // Add table qualifiers (database.schema.table)
            for (com.calcite_new.core.model.Identifier ns : sourceColumn.getNamespace()) {
                qualifiedNames.add(ns.getNormalizedName());
            }

            // Add column name
            qualifiedNames.add(columnName);

            log.debug("Created SqlColumnIdentifier with qualified names: {}", qualifiedNames);

            return new SqlColumnIdentifier(
                    qualifiedNames,
                    pos,
                    sourceColumn,
                    parentTableEntity
            );
        }

        // Fallback: if we can't trace to source, return without creating virtual entities
        // This prevents CTEs from appearing in relationships
        log.warn("Could not trace CTE column {}.{} to source - sourceColumn or sourceEntity is null", alias, columnName);
        return new SqlColumnIdentifier(
                Arrays.asList(alias, columnName),
                pos,
                null,  // NO source column
                null   // NO database entity - prevents CTE from appearing in relationships
        );
    }

    /**
     * Qualifies a table name using default qualifiers.
     */
    private List<String> qualifyTableName(String tableName) {
        List<String> qualifiers = new ArrayList<>();

        if (defaultQualifiers.getDatabase() != null) {
            qualifiers.add(defaultQualifiers.getDatabase());
        }
        if (defaultQualifiers.getSchema() != null) {
            qualifiers.add(defaultQualifiers.getSchema());
        }
        qualifiers.add(tableName);

        EntityQualifier entityQualifier = getEntityQualifier(qualifiers);
        List<Identifier> qualifiedIdentifiers = entityQualifier.getQualifiers();

        List<String> result = new ArrayList<>();
        for (int i = 1; i < qualifiedIdentifiers.size(); i++) {
            result.add(qualifiedIdentifiers.get(i).getNormalizedName());
        }

        return result;
    }

    /**
     * Creates an EntityQualifier for the given qualifiers.
     */
    private EntityQualifier getEntityQualifier(List<String> qualifiers) {
        List<String> defaultQualifiersList = new ArrayList<>();
        if (defaultQualifiers.getDatabase() != null) {
            defaultQualifiersList.add(defaultQualifiers.getDatabase());
        }
        if (defaultQualifiers.getSchema() != null) {
            defaultQualifiersList.add(defaultQualifiers.getSchema());
        }

        return new EntityQualifier(qualifiers, defaultQualifiersList, dialect);
    }

    /**
     * Resolves the entity using EntityResolver and returns the DatabaseEntity.
     */
    private DatabaseEntity resolveEntity(List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return null;
        }

        try {
            // Create qualifiers list for EntityResolver
            List<String> qualifiers = new ArrayList<>();

            if (qualifiedNames.size() >= 3) {
                qualifiers.add(qualifiedNames.get(0)); // database
                qualifiers.add(qualifiedNames.get(1)); // schema
                qualifiers.add(qualifiedNames.get(2)); // table/view
            } else if (qualifiedNames.size() == 2) {
                // Add default database if not provided
                if (defaultQualifiers.getDatabase() != null) {
                    qualifiers.add(defaultQualifiers.getDatabase());
                }
                qualifiers.add(qualifiedNames.get(0)); // schema
                qualifiers.add(qualifiedNames.get(1)); // table/view
            } else if (qualifiedNames.size() == 1) {
                // Add default database and schema if not provided
                if (defaultQualifiers.getDatabase() != null) {
                    qualifiers.add(defaultQualifiers.getDatabase());
                }
                if (defaultQualifiers.getSchema() != null) {
                    qualifiers.add(defaultQualifiers.getSchema());
                }
                qualifiers.add(qualifiedNames.get(0)); // table/view
            }

            List<String> defaultQualifiersList = new ArrayList<>();
            EntityQualifier qualifier = new EntityQualifier(qualifiers, defaultQualifiersList, dialect);
            return entityResolver.resolve(qualifier);
        } catch (Exception e) {
            log.debug("Failed to resolve entity for {}: {}", qualifiedNames, e.getMessage());
            return null;
        }
    }

    /**
     * Creates a Column entity from a table/view entity and column name.
     */
    private Column createColumnEntity(DatabaseEntity tableEntity, String columnName) {
        if (tableEntity == null || columnName == null) {
            return null;
        }

        try {
            // Create column namespace from table entity
            List<Identifier> columnNamespace = new ArrayList<>();
            if (tableEntity.getNamespace() != null) {
                columnNamespace.addAll(tableEntity.getNamespace());
            }
            columnNamespace.add(tableEntity.getName());

            // Create the column entity
            return Column.builder()
                    .namespace(columnNamespace)
                    .name(Identifier.of(columnName, dialect))
                    .dialect(dialect)
                    .ordinalPosition(0) // We don't have ordinal position info, use 0
                    .type(DataType.create(org.apache.calcite.sql.type.SqlTypeName.VARCHAR)) // Default type, could be enhanced
                    .build();
        } catch (Exception e) {
            log.warn("Failed to create column entity for {}.{}: {}",
                    tableEntity.getName().getName(), columnName, e.getMessage());
            return null;
        }
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
