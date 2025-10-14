package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.sql.core.processor.visitor.scope.ComputedColumnInfo;
import lombok.Getter;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.List;

/**
 * A specialized SqlIdentifier for SELECT list column alias references.
 * Carries a reference to the source expression from the SELECT list.
 */
@Getter
public class SqlComputedColumnIdentifier extends SqlIdentifier {

    private final SqlNode sourceExpression;
    private final String aliasName;
    private final ComputedColumnInfo.ExpressionType expressionType;

    private SqlComputedColumnIdentifier(
            String aliasName,
            SqlNode sourceExpression,
            ComputedColumnInfo.ExpressionType expressionType,
            SqlParserPos pos) {
        super(aliasName, pos);
        this.aliasName = aliasName;
        this.sourceExpression = sourceExpression;
        this.expressionType = expressionType;
    }

    private SqlComputedColumnIdentifier(
            List<String> names,
            SqlNode sourceExpression,
            ComputedColumnInfo.ExpressionType expressionType,
            SqlParserPos pos) {
        super(names, pos);
        this.aliasName = names != null && !names.isEmpty() ? names.get(names.size() - 1) : null;
        this.sourceExpression = sourceExpression;
        this.expressionType = expressionType;
    }

    public static SqlComputedColumnIdentifier from(
            ComputedColumnInfo columnInfo,
            String aliasName,
            SqlParserPos pos) {
        return new SqlComputedColumnIdentifier(
                aliasName,
                columnInfo.getExpression(),
                columnInfo.getExpressionType(),
                pos
        );
    }

    public static SqlComputedColumnIdentifier from(
            ComputedColumnInfo columnInfo,
            List<String> names,
            SqlParserPos pos) {
        return new SqlComputedColumnIdentifier(
                names,
                columnInfo.getExpression(),
                columnInfo.getExpressionType(),
                pos
        );
    }

    public boolean isSimpleColumnAlias() {
        return expressionType == ComputedColumnInfo.ExpressionType.SIMPLE_COLUMN;
    }

    public boolean isWindowFunction() {
        return expressionType == ComputedColumnInfo.ExpressionType.WINDOW_FUNCTION;
    }

    public boolean isAggregateFunction() {
        return expressionType == ComputedColumnInfo.ExpressionType.AGGREGATE_FUNCTION;
    }

    public boolean isComputed() {
        return expressionType != ComputedColumnInfo.ExpressionType.SIMPLE_COLUMN;
    }

    @Override
    public String toString() {
        return String.format("SqlComputedColumnIdentifier(%s -> %s, type: %s)",
                aliasName,
                sourceExpression != null ? sourceExpression.toString() : "null",
                expressionType);
    }
}

