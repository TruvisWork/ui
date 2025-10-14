package com.calcite_new.sql.core.processor.utils;

import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private boolean checkEqualityJoinCondition(List<SqlNode> operands) {
        if (operands.size() != 2) {
            return false;
        }

        ColumnInfo leftCol = resolveColumn(operands.get(0));
        ColumnInfo rightCol = resolveColumn(operands.get(1));

        return leftCol != null && rightCol != null &&
                leftCol.isStringType() && rightCol.isStringType();
    }

    @Getter
    @AllArgsConstructor
    private static class ColumnInfo {
        private final String tableName;
        private final String columnName;
        private final SqlTypeName sqlTypeName;

        public boolean isStringType() {
            return sqlTypeName == SqlTypeName.VARCHAR || sqlTypeName == SqlTypeName.CHAR;
        }
    }

    private ColumnInfo resolveColumn(SqlNode node) {
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

            return new ColumnInfo(
                    tableName,
                    columnName,
                    column.getType().getName()
            );
        } catch (Exception e) {
            return null;
        }
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
}