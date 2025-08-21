package com.calcite_new.sql.core.processor.utils;

import org.apache.calcite.sql.*;

import java.util.List;

public class SqlConditionUtils {

    @SuppressWarnings("unchecked")
    public static boolean isConditionAlwaysTrue(SqlNode condition) {
        if (condition == null) return false;

        if (condition instanceof SqlIdentifier id) {
            return "TRUE".equalsIgnoreCase(id.toString());
        }

        if (condition instanceof SqlBasicCall call) {
            SqlOperator op = call.getOperator();
            List<SqlNode> operands = call.getOperandList();

            if (operands.size() == 2 && operands.get(0) instanceof SqlLiteral leftLit && operands.get(1) instanceof SqlLiteral rightLit) {
                Object leftVal = leftLit.getValue();
                Object rightVal = rightLit.getValue();

                if (leftVal instanceof Comparable && rightVal instanceof Comparable) {
                    Comparable<Object> left = (Comparable<Object>) leftVal;
                    Comparable<Object> right = (Comparable<Object>) rightVal;

                    return switch (op.getName()) {
                        case "=" -> left.compareTo(right) == 0;
                        case ">" -> left.compareTo(right) > 0;
                        case "<" -> left.compareTo(right) < 0;
                        case ">=" -> left.compareTo(right) >= 0;
                        case "<=" -> left.compareTo(right) <= 0;
                        default -> false;
                    };
                }
            }
        }

        return false;
    }

    public static boolean hasCaseInsensitiveComparison(SqlNode condition) {
        if (condition == null) return false;

        if (condition instanceof SqlBasicCall call) {
            SqlOperator operator = call.getOperator();
            List<SqlNode> operands = call.getOperandList();

            // Check for basic equality and IN operators
            if (("=".equals(operator.getName()) || "IN".equals(operator.getName())) && operands.size() >= 2) {
                SqlNode left = operands.get(0);
                SqlNode right = operands.get(1);

                // Case 1: LOWER/UPPER(column) = 'value' or LOWER/UPPER(column) IN ('val1', 'val2')
                if (isCaseFunction(left)) {
                    return true;
                }

                // Case 2: 'value' = LOWER/UPPER(column)
                if (isCaseFunction(right)) {
                    return true;
                }

                // Case 3: LOWER/UPPER(column1) = LOWER/UPPER(column2)
                if (isCaseFunction(left) && isCaseFunction(right)) {
                    return true;
                }
            }

            // Recursively check all operands for case-insensitive comparisons
            for (SqlNode operand : operands) {
                if (hasCaseInsensitiveComparison(operand)) {
                    return true;
                }
            }

            // Check for complex case-insensitive conditions in nested calls
            if ("OR".equals(operator.getName()) || "AND".equals(operator.getName())) {
                return operands.stream().anyMatch(SqlConditionUtils::hasCaseInsensitiveComparison);
            }
        }

        return false;
    }

    private static boolean isCaseFunction(SqlNode node) {
        if (node instanceof SqlBasicCall call) {
            String funcName = call.getOperator().getName();
            return "LOWER".equalsIgnoreCase(funcName) || "UPPER".equalsIgnoreCase(funcName);
        }
        return false;
    }
}
