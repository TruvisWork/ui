package com.calcite_new.sql.core.processor.utils;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.SqlBasicCall;

import java.util.*;

/**
 * Utility class to detect if functions are applied to partition columns in WHERE clauses.
 * This helps identify queries that might have performance issues due to function usage on partition columns.
 */
public class PartitionColumnFunctionDetector {

    private final EntityCatalog entityCatalog;
    private final Map<String, Table> tableRegistry;
    private final Dialect dialect;
    private final String defaultDatabase;
    private final String defaultSchema;

    public PartitionColumnFunctionDetector(EntityCatalog entityCatalog, String defaultDatabase, String defaultSchema) {
        this.entityCatalog = entityCatalog;
        this.tableRegistry = new HashMap<>();
        this.dialect = new BigQuerySqlDialect();
        this.defaultDatabase = defaultDatabase;
        this.defaultSchema = defaultSchema;
    }

    public void registerTable(String alias, EntityQualifier qualifier) {
        try {
            DatabaseEntity entity = entityCatalog.getDatabaseEntity(qualifier);
            if (entity instanceof Table table) {
                tableRegistry.put(alias, table);
            }
        } catch (Exception e) {
            // Skip if Table not found in catalog
        }
    }

    public void registerTable(SqlIdentifier tableId) {
        if (tableId == null || tableId.names == null || tableId.names.isEmpty()) {
            return;
        }
        
        List<String> qualifiers = new ArrayList<>(tableId.names);
        String alias = tableId.names.get(tableId.names.size() - 1);
        EntityQualifier qualifier = new EntityQualifier(qualifiers, 
            List.of(defaultDatabase, defaultSchema), dialect);
        
        registerTable(alias, qualifier);
    }

    public void registerTable(SqlNode node) {
        if (node == null) {
            return;
        }
        
        if (node instanceof SqlIdentifier identifier) {
            registerTable(identifier);
        } else if (node instanceof SqlBasicCall call) {
            if (call.getOperator().getName().equalsIgnoreCase("AS") && 
                !call.getOperandList().isEmpty() && 
                call.getOperandList().get(0) instanceof SqlIdentifier tableId) {
                registerTable(tableId);
            }
        }
    }

    public boolean hasFunctionOnPartitionColumn(SqlNode whereClause) {
        if (whereClause == null) {
            return false;
        }
        
        return analyzeSqlNode(whereClause);
    }

    private boolean analyzeSqlNode(SqlNode node) {
        if (node == null) {
            return false;
        }

        if (node instanceof SqlBasicCall call) {
            return analyzeCall(call);
        } else if (node instanceof SqlNodeList nodeList) {
            for (SqlNode child : nodeList) {
                if (analyzeSqlNode(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean analyzeCall(SqlBasicCall call) {
        SqlOperator operator = call.getOperator();

        if (isLogicalOrComparisonOperator(operator)) {
            for (SqlNode operand : call.getOperandList()) {
                if (analyzeSqlNode(operand)) {
                    return true;
                }
            }
            return false;
        }

        if (isFunction(operator)) {
            for (SqlNode operand : call.getOperandList()) {
                if (isPartitionColumn(operand)) {
                    return true;
                }
            }
        }

        for (SqlNode operand : call.getOperandList()) {
            if (analyzeSqlNode(operand)) {
                return true;
            }
        }

        return false;
    }

    private boolean isLogicalOrComparisonOperator(SqlOperator operator) {
        if (operator == null) {
            return false;
        }
        
        String opName = operator.getName().toUpperCase();
        return opName.equals("AND") || opName.equals("OR") || opName.equals("NOT") ||
               opName.equals("=") || opName.equals("<>") || opName.equals("!=") ||
               opName.equals("<") || opName.equals(">") || opName.equals("<=") || 
               opName.equals(">=") || opName.equals("IN") || opName.equals("NOT IN") ||
               opName.equals("BETWEEN") || opName.equals("NOT BETWEEN") ||
               opName.equals("LIKE") || opName.equals("NOT LIKE") ||
               opName.equals("IS NULL") || opName.equals("IS NOT NULL");
    }

    private boolean isFunction(SqlOperator operator) {
        if (operator == null) {
            return false;
        }

        SqlKind kind = operator.getKind();

        return kind == SqlKind.CAST ||
               kind == SqlKind.EXTRACT ||
               kind == SqlKind.OTHER_FUNCTION ||
               kind == SqlKind.TRIM ||
               kind == SqlKind.FLOOR ||
               kind == SqlKind.CEIL ||
               (kind == SqlKind.OTHER && !isLogicalOrComparisonOperator(operator));
    }

    private boolean isPartitionColumn(SqlNode node) {
        if (node == null) {
            return false;
        }

        if (node instanceof SqlIdentifier identifier) {
            return isPartitionColumnIdentifier(identifier);
        }

        return false;
    }

    private boolean isPartitionColumnIdentifier(SqlIdentifier identifier) {
        if (identifier == null || identifier.names == null || identifier.names.isEmpty()) {
            return false;
        }

        String columnName;
        String tableAlias = null;

        if (identifier.names.size() >= 2) {
            tableAlias = identifier.names.get(identifier.names.size() - 2);
            columnName = identifier.names.get(identifier.names.size() - 1);
        } else {
            columnName = identifier.names.get(0);
        }

        if (tableAlias != null) {
            Table table = tableRegistry.get(tableAlias);
            if (table != null) {
                return isColumnInPartitionList(table, columnName);
            }
        } else {
            for (Table table : tableRegistry.values()) {
                if (isColumnInPartitionList(table, columnName)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isColumnInPartitionList(Table table, String columnName) {
        if (table == null || columnName == null) {
            return false;
        }

        List<String> partitionedColumns = table.getPartitionedColumns();
        if (partitionedColumns == null || partitionedColumns.isEmpty()) {
            return false;
        }

        String normalizedColumnName = columnName.toLowerCase();
        return partitionedColumns.stream()
            .anyMatch(pc -> pc.toLowerCase().equals(normalizedColumnName));
    }

    public void clearRegistry() {
        tableRegistry.clear();
    }
}
