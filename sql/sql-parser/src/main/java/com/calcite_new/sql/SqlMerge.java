package com.calcite_new.sql;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.util.ImmutableNullableList;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SqlMerge extends SqlCall {
    private final SqlNode targetTable;
    private final SqlNode condition;
    private final SqlNode source;
    private final @Nullable SqlInsert insertCall;
    private final @Nullable SqlIdentifier alias;
    private final List<MatchedClause> matchedClauses;
    SqlNode insertCondition;

    public static class MatchedClause {
        public final @Nullable SqlNode condition;
        public final SqlNode action; // SqlUpdate or SqlDelete

        public MatchedClause(@Nullable SqlNode condition, SqlNode action) {
            this.condition = condition;
            this.action = action;
        }
    }

    public SqlMerge(SqlParserPos pos,
                    SqlNode targetTable,
                    SqlNode condition,
                    SqlNode source,
                    List<MatchedClause> matchedClauses,
                    @Nullable SqlInsert insertCall,
                    @Nullable SqlIdentifier alias,
                    SqlNode insertCondition) {
        super(pos);
        this.targetTable = targetTable;
        this.condition = condition;
        this.source = source;
        this.matchedClauses = matchedClauses;
        this.insertCall = insertCall;
        this.alias = alias;
        this.insertCondition = insertCondition;
    }

    public SqlNode getTargetTable() {
        return targetTable;
    }

    public SqlNode getCondition() {
        return condition;
    }

    public SqlNode getSource() {
        return source;
    }

    public @Nullable SqlInsert getInsertCall() {
        return insertCall;
    }

    public @Nullable SqlIdentifier getAlias() {
        return alias;
    }

    public List<MatchedClause> getMatchedClauses() {
        return matchedClauses;
    }

    public SqlNode getInsertCondition() {
        return insertCondition;
    }

    @Override
    public SqlOperator getOperator() {
        return new SqlSpecialOperator("MERGE", SqlKind.MERGE);
    }

    @Override
    public SqlKind getKind() {
        return SqlKind.MERGE;
    }

    @Override
    public SqlParserPos getParserPosition() {
        return pos;
    }

    @Override
    public List<@Nullable SqlNode> getOperandList() {
        List<@Nullable SqlNode> ops = new ArrayList<>();
        ops.add(targetTable);
        ops.add(condition);
        ops.add(source);
        ops.add(insertCall);
        ops.add(alias);
        for (MatchedClause clause : matchedClauses) {
            ops.add(clause.condition);
            ops.add(clause.action);
        }
        return ImmutableNullableList.copyOf(ops);
    }

    @Override
    public void setOperand(int i, @Nullable SqlNode operand) {
        throw new UnsupportedOperationException("setOperand not supported for SqlMerge with multiple WHEN clauses");
    }

    @Override
    public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
        final SqlWriter.Frame frame =
                writer.startList(SqlWriter.FrameTypeEnum.SELECT, "MERGE INTO", "");

        targetTable.unparse(writer, leftPrec, rightPrec);
 /*       if (alias != null) {
            writer.keyword("AS");
            alias.unparse(writer, leftPrec, rightPrec);
        }*/

        writer.newlineAndIndent();
        writer.keyword("USING");
        source.unparse(writer, leftPrec, rightPrec);

        writer.newlineAndIndent();
        writer.keyword("ON");
        condition.unparse(writer, leftPrec, rightPrec);

        for (MatchedClause clause : matchedClauses) {
            writer.newlineAndIndent();
            writer.keyword("WHEN MATCHED");
            if (clause.condition != null) {
                writer.keyword("AND");
                clause.condition.unparse(writer, leftPrec, rightPrec);
            }
            writer.keyword("THEN");

            if (clause.action instanceof SqlUpdate) {
                writer.keyword("UPDATE");
                SqlUpdate update = (SqlUpdate) clause.action;

                final SqlWriter.Frame setFrame = writer.startList(SqlWriter.FrameTypeEnum.UPDATE_SET_LIST, "SET", "");
                List<SqlNode> targets = update.getTargetColumnList();
                List<SqlNode> sources = update.getSourceExpressionList();
                for (int i = 0; i < targets.size(); i++) {
                    if (i > 0) writer.sep(",");
                    targets.get(i).unparse(writer, leftPrec, rightPrec);
                    writer.keyword("=");
                    sources.get(i).unparse(writer, leftPrec, rightPrec);
                }
                writer.endList(setFrame);

            } else if (clause.action instanceof SqlDelete) {
                writer.keyword("DELETE");
            }
        }

        if (insertCall != null) {
            writer.newlineAndIndent();
            writer.keyword("WHEN NOT MATCHED");

            if (insertCondition != null) {
                writer.keyword("AND");
                insertCondition.unparse(writer, leftPrec, rightPrec);
            }

            writer.keyword("THEN INSERT");

            SqlNodeList cols = insertCall.getTargetColumnList();
            if (cols != null && !cols.isEmpty()) {
                writer.print("(");
                cols.unparse(writer, leftPrec, rightPrec);
                writer.print(")");
            }

            // Ensure VALUES syntax is preserved
            insertCall.getSource().unparse(writer, 0, 0);
        }

        writer.endList(frame);
    }
}