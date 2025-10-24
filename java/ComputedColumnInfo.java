package com.calcite_new.sql.core.processor.visitor.scope;

import com.calcite_new.sql.SqlColumnIdentifier;
import lombok.Getter;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlNode;

/**
 * Represents information about a SELECT list column alias.
 * Automatically analyzes the expression to determine its type.
 */
@Getter
public class ComputedColumnInfo {

    private final String aliasName;
    private final SqlNode expression;
    private final ExpressionType expressionType;

    public enum ExpressionType {
        SIMPLE_COLUMN,
        WINDOW_FUNCTION,
        AGGREGATE_FUNCTION,
        REGULAR_FUNCTION,
        ARITHMETIC_EXPR,
        OTHER_EXPRESSION
    }

    public ComputedColumnInfo(String aliasName, SqlNode expression) {
        this.aliasName = aliasName;
        this.expression = expression;
        this.expressionType = determineExpressionType(expression);
    }

    private ExpressionType determineExpressionType(SqlNode expression) {
        if (expression == null) {
            return ExpressionType.OTHER_EXPRESSION;
        }

        if (expression instanceof SqlColumnIdentifier ||
                (expression instanceof SqlIdentifier && !(expression instanceof SqlColumnIdentifier))) {
            return ExpressionType.SIMPLE_COLUMN;
        }

        if (expression instanceof SqlBasicCall call && call.getOperator() != null && call.getOperator().getKind() != null) {
            SqlKind kind = call.getOperator().getKind();

            if (kind == SqlKind.OVER) {
                return ExpressionType.WINDOW_FUNCTION;
            }

            if (isAggregateKind(kind)) {
                return ExpressionType.AGGREGATE_FUNCTION;
            }
        }

        return ExpressionType.OTHER_EXPRESSION;
    }

    private boolean isAggregateKind(SqlKind kind) {
        return kind == SqlKind.SUM || kind == SqlKind.COUNT ||
                kind == SqlKind.AVG || kind == SqlKind.MAX || kind == SqlKind.MIN || kind == SqlKind.STDDEV_POP || kind == SqlKind.STDDEV_SAMP
                || kind == SqlKind.VAR_POP || kind == SqlKind.VAR_SAMP ||
                kind == SqlKind.GROUP_ID || kind == SqlKind.GROUPING || kind == SqlKind.GROUPING_ID ||
                kind == SqlKind.COLLECT || kind == SqlKind.LISTAGG;
    }

    public boolean isSimpleColumnAlias() {
        return expressionType == ExpressionType.SIMPLE_COLUMN;
    }

    public boolean isWindowFunction() {
        return expressionType == ExpressionType.WINDOW_FUNCTION;
    }

    public boolean isAggregateFunction() {
        return expressionType == ExpressionType.AGGREGATE_FUNCTION;
    }

    public boolean isComputed() {
        return expressionType != ExpressionType.SIMPLE_COLUMN;
    }
}

