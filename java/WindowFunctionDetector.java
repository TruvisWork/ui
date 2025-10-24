package com.calcite_new.sql.core.processor.utils;

import com.calcite_new.sql.model.entity.context.function.WindowFunctionInfo;
import org.apache.calcite.sql.*;

import java.util.*;

/**
 * Utility class to detect and extract window function information from SELECT projections.
 * This helps identify queries that compute window functions but don't filter on them,
 * which can be wasteful if the window function is expensive to compute.
 */
public class WindowFunctionDetector {

    public List<WindowFunctionInfo> extractWindowFunctions(SqlNodeList selectList, SqlNode whereClause) {
        List<WindowFunctionInfo> windowFunctions = new ArrayList<>();
        
        if (selectList == null || selectList.isEmpty()) {
            return windowFunctions;
        }

        Set<String> columnsInWhere = extractColumnReferences(whereClause);

        for (SqlNode selectItem : selectList) {
            WindowFunctionInfo info = extractWindowFunctionInfo(selectItem, columnsInWhere);
            if (info != null) {
                windowFunctions.add(info);
            }
        }

        return windowFunctions;
    }

    public boolean hasUnusedWindowFunction(SqlNodeList selectList, SqlNode whereClause) {
        List<WindowFunctionInfo> windowFunctions = extractWindowFunctions(selectList, whereClause);
        return windowFunctions.stream().anyMatch(wf -> !Boolean.TRUE.equals(wf.getUsedInWhere()));
    }

    private WindowFunctionInfo extractWindowFunctionInfo(SqlNode selectItem, Set<String> columnsInWhere) {
        if (selectItem instanceof SqlBasicCall call) {
            if (call.getOperator().getName().equalsIgnoreCase("AS") && 
                call.getOperandList().size() >= 2) {
                
                SqlNode function = call.getOperandList().get(0);
                SqlNode aliasNode = call.getOperandList().get(1);
                
                if (isWindowFunction(function)) {
                    String alias = null;
                    if (aliasNode instanceof SqlIdentifier identifier) {
                        alias = identifier.getSimple();
                    }
                    return buildWindowFunctionInfo(function, alias, columnsInWhere);
                }
            } else if (isWindowFunction(call)) {
                return buildWindowFunctionInfo(call, null, columnsInWhere);
            }
        }
        
        return null;
    }

    private WindowFunctionInfo buildWindowFunctionInfo(SqlNode windowFunctionNode, 
                                                       String alias, 
                                                       Set<String> columnsInWhere) {
        if (!(windowFunctionNode instanceof SqlBasicCall call)) {
            return null;
        }

        String functionName = null;
        List<String> partitionByColumns = new ArrayList<>();
        List<String> orderByColumns = new ArrayList<>();

        SqlOperator operator = call.getOperator();
        
        if (operator.getKind() == SqlKind.OVER) {
            if (!call.getOperandList().isEmpty()) {
                SqlNode functionNode = call.getOperandList().get(0);
                if (functionNode instanceof SqlBasicCall funcCall) {
                    functionName = funcCall.getOperator().getName();
                }
            }
            
            if (call.getOperandList().size() >= 2) {
                SqlNode windowSpec = call.getOperandList().get(1);
                extractWindowSpecification(windowSpec, partitionByColumns, orderByColumns);
            }
        } else {
            functionName = operator.getName();
            
            for (SqlNode operand : call.getOperandList()) {
                if (operand instanceof SqlBasicCall operandCall && 
                    operandCall.getOperator().getKind() == SqlKind.OVER) {
                    extractWindowSpecification(operandCall, partitionByColumns, orderByColumns);
                    break;
                }
            }
        }

        boolean usedInWhere = alias != null && columnsInWhere.contains(alias.toLowerCase());

        return WindowFunctionInfo.builder()
                .functionName(functionName)
                .partitionByCol(partitionByColumns.isEmpty() ? null : partitionByColumns)
                .orderByCol(orderByColumns.isEmpty() ? null : orderByColumns)
                .alias(alias)
                .usedInWhere(usedInWhere)
                .build();
    }

    private void extractWindowSpecification(SqlNode windowSpec, 
                                           List<String> partitionByColumns, 
                                           List<String> orderByColumns) {
        if (!(windowSpec instanceof SqlWindow window)) {
            return;
        }

        SqlNodeList partitionList = window.getPartitionList();
        if (partitionList != null) {
            for (SqlNode partitionNode : partitionList) {
                String columnName = extractColumnName(partitionNode);
                if (columnName != null) {
                    partitionByColumns.add(columnName);
                }
            }
        }

        SqlNodeList orderList = window.getOrderList();
        if (orderList != null) {
            for (SqlNode orderNode : orderList) {
                String columnName = extractColumnName(orderNode);
                if (columnName != null) {
                    orderByColumns.add(columnName);
                }
            }
        }
    }

    private String extractColumnName(SqlNode node) {
        if (node instanceof SqlIdentifier identifier) {
            if (identifier.names != null && !identifier.names.isEmpty()) {
                return identifier.names.get(identifier.names.size() - 1);
            }
        } else if (node instanceof SqlBasicCall call) {
            if (!call.getOperandList().isEmpty()) {
                return extractColumnName(call.getOperandList().get(0));
            }
        }
        return null;
    }

    private boolean isWindowFunction(SqlNode node) {
        if (node instanceof SqlBasicCall call) {
            SqlOperator operator = call.getOperator();

            if (operator.getKind() == SqlKind.OVER) {
                return true;
            }

            String opName = operator.getName().toUpperCase();
            if (opName.equals("ROW_NUMBER") || opName.equals("RANK") || 
                opName.equals("DENSE_RANK") || opName.equals("NTILE") ||
                opName.equals("LAG") || opName.equals("LEAD") ||
                opName.equals("FIRST_VALUE") || opName.equals("LAST_VALUE") ||
                opName.equals("NTH_VALUE")) {

                for (SqlNode operand : call.getOperandList()) {
                    if (operand instanceof SqlBasicCall operandCall && 
                        operandCall.getOperator().getKind() == SqlKind.OVER) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    private Set<String> extractColumnReferences(SqlNode node) {
        Set<String> columns = new HashSet<>();
        if (node == null) {
            return columns;
        }

        collectColumnReferences(node, columns);
        return columns;
    }

    private void collectColumnReferences(SqlNode node, Set<String> columns) {
        if (node == null) {
            return;
        }

        if (node instanceof SqlIdentifier identifier) {
            if (identifier.names != null && !identifier.names.isEmpty()) {
                String columnName = identifier.names.get(identifier.names.size() - 1);
                columns.add(columnName.toLowerCase());
            }
        } else if (node instanceof SqlBasicCall call) {
            for (SqlNode operand : call.getOperandList()) {
                collectColumnReferences(operand, columns);
            }
        } else if (node instanceof SqlNodeList nodeList) {
            for (SqlNode child : nodeList) {
                collectColumnReferences(child, columns);
            }
        }
    }
}
