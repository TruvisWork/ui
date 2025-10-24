package com.calcite_new.sql.core.processor.utils;

import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.sql.model.entity.ColumnInfo;
import com.calcite_new.sql.model.enums.ClauseType;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

public class JoinAnalyzer {
    private final EntityCatalog entityCatalog;
    private final Map<String, String> tableAliases = new HashMap<>();
    private final Map<String, Table> resolvedTables = new HashMap<>();

    public JoinAnalyzer(EntityCatalog entityCatalog) {
        this.entityCatalog = Objects.requireNonNull(entityCatalog, "EntityCatalog cannot be null");
    }

    public void registerTableAlias(String alias, EntityQualifier qualifier) {
        if (alias == null || qualifier == null) {
            throw new IllegalArgumentException("Alias and qualifier cannot be null");
        }

        String normalizedAlias = Identifier.of(alias, new BigQuerySqlDialect()).getNormalizedName();
        DatabaseEntity entity = entityCatalog.getDatabaseEntity(qualifier);

        if (!(entity instanceof Table)) {
            throw new IllegalArgumentException("Table not found: " + qualifier);
        }

        String tableName = entity.getName().getNormalizedName();
        tableAliases.put(normalizedAlias, tableName);
        resolvedTables.put(tableName, (Table) entity);
    }

    public boolean isJoinOnStringColumn(SqlNode condition) {
        if (condition == null) {
            return false;
        }

        if (!(condition instanceof SqlBasicCall call)) {
            return false;
        }

        SqlOperator operator = call.getOperator();
        List<SqlNode> operands = call.getOperandList();

        if ("=".equals(operator.getName())) {
            return checkEqualityJoinCondition(operands);
        } else if ("AND".equalsIgnoreCase(operator.getName())) {
            return operands.stream()
                    .anyMatch(this::isJoinOnStringColumn);
        }

        return false;
    }

    /**
     * Extracts all column references involved in the join condition. For equality joins chained with AND,
     * this returns one JoinColumnInfo per column identifier encountered.
     */
    public List<ColumnInfo> extractJoinColumns(SqlNode condition) {
        List<ColumnInfo> columns = new ArrayList<>();
        if (condition instanceof SqlBasicCall call) {
            SqlOperator operator = call.getOperator();
            List<SqlNode> operands = call.getOperandList();

            if ("=".equals(operator.getName())) {
                if (operands.size() == 2) {
                    ResolvedColumn leftCol = resolveColumn(operands.get(0));
                    ResolvedColumn rightCol = resolveColumn(operands.get(1));
                    addUnique(columns, toColumnInfo(leftCol));
                    addUnique(columns, toColumnInfo(rightCol));
                }
            } else if ("AND".equalsIgnoreCase(operator.getName())) {
                for (SqlNode operand : operands) {
                    for (ColumnInfo c : extractJoinColumns(operand)) {
                        addUnique(columns, c);
                    }
                }
            } else {
                // For other operators, fall through to generic identifier collection
            }
        }

        // Generic collection to be robust for unexpected trees (e.g., functions, casts, different operators)
        if (columns.isEmpty()) {
            collectIdentifiers(condition, columns);
        }

        return columns;
    }

    private void collectIdentifiers(SqlNode node, List<ColumnInfo> out) {
        if (node == null) return;
        if (node instanceof SqlIdentifier) {
            ColumnInfo ci = toColumnInfo(resolveColumn(node));
            addUnique(out, ci);
        } else if (node instanceof SqlBasicCall call) {
            for (SqlNode op : call.getOperandList()) {
                collectIdentifiers(op, out);
            }
        } else if (node instanceof SqlCall call) {
            for (SqlNode op : call.getOperandList()) {
                collectIdentifiers(op, out);
            }
        }
    }

    private void addUnique(List<ColumnInfo> list, ColumnInfo candidate) {
        if (candidate == null) return;
        if (!list.contains(candidate)) {
            list.add(candidate);
        }
    }

    private boolean checkEqualityJoinCondition(List<SqlNode> operands) {
        if (operands.size() != 2) {
            return false;
        }

        ResolvedColumn leftCol = resolveColumn(operands.get(0));
        ResolvedColumn rightCol = resolveColumn(operands.get(1));

        return leftCol != null && rightCol != null &&
                isStringType(leftCol) && isStringType(rightCol);
    }

    private ResolvedColumn resolveColumn(SqlNode node) {
        if (!(node instanceof SqlIdentifier id)) {
            return null;
        }

        try {
            String tableName;
            String columnName;

            if (id.names.size() > 1) {
                String qualifier = Identifier.of(id.names.get(0), new BigQuerySqlDialect()).getNormalizedName();
                columnName = Identifier.of(id.names.get(1), new BigQuerySqlDialect()).getNormalizedName();
                tableName = tableAliases.getOrDefault(qualifier, qualifier);
            } else {
                columnName = Identifier.of(id.names.get(0), new BigQuerySqlDialect()).getNormalizedName();
                tableName = findTableForColumn(columnName);
            }

            if (tableName == null) {
                return null;
            }

            Table table = resolvedTables.get(tableName);
            if (table == null) {
                return null;
            }

            Column column = findColumn(table, columnName);
            if (column == null) {
                return null;
            }

            String product = null;
            String database = null;
            String schema = null;
            var ns = table.getNamespace();
            if (ns != null) {
                if (ns.size() > 0) product = ns.get(0).getNormalizedName();
                if (ns.size() > 1) database = ns.get(1).getNormalizedName();
                if (ns.size() > 2) schema = ns.get(2).getNormalizedName();
            }

            return new ResolvedColumn(product, database, schema, tableName, columnName, column.getType().getName());
        } catch (Exception e) {
            return null;
        }
    }

    private ColumnInfo toColumnInfo(ResolvedColumn resolvedColumn) {
        if (resolvedColumn == null) {
            return null;
        }

        return ColumnInfo.builder()
                .product(resolvedColumn.product)
                .database(resolvedColumn.database)
                .schema(resolvedColumn.schema)
                .tableName(resolvedColumn.tableName)
                .columnName(resolvedColumn.columnName)
                .clause(ClauseType.JOIN)
                .build();
    }

    private boolean isStringType(ResolvedColumn column) {
        if (column == null || column.sqlTypeName == null) {
            return false;
        }
        return column.sqlTypeName == SqlTypeName.VARCHAR || column.sqlTypeName == SqlTypeName.CHAR;
    }

    private String findTableForColumn(String columnName) {
        return resolvedTables.entrySet().stream()
                .filter(entry -> hasColumn(entry.getValue(), columnName))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private boolean hasColumn(Table table, String columnName) {
        return findColumn(table, columnName) != null;
    }

    private Column findColumn(Table table, String columnName) {
        return table.getColumns().stream()
                .filter(col -> col.getName().getNormalizedName().equals(columnName))
                .findFirst()
                .orElse(null);
    }

    private static class ResolvedColumn {
        private final String product;
        private final String database;
        private final String schema;
        private final String tableName;
        private final String columnName;
        private final SqlTypeName sqlTypeName;

        private ResolvedColumn(String product, String database, String schema,
                               String tableName, String columnName, SqlTypeName sqlTypeName) {
            this.product = product;
            this.database = database;
            this.schema = schema;
            this.tableName = tableName;
            this.columnName = columnName;
            this.sqlTypeName = sqlTypeName;
        }
    }
}