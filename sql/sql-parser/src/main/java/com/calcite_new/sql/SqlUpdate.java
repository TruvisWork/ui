package com.calcite_new.sql;

import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlWriter;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.util.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SqlUpdate extends org.apache.calcite.sql.SqlUpdate {
  private final SqlNode source;

  public SqlUpdate(SqlParserPos pos,
                   SqlNode targetTable,
                   SqlNodeList targetColumnList,
                   SqlNodeList sourceExpressionList,
                   @Nullable SqlNode condition,
                   @Nullable SqlNode source,
                   @Nullable SqlIdentifier alias) {
    super(pos, targetTable, targetColumnList, sourceExpressionList, condition, null, alias);
    this.source = source;
  }

  public SqlNode getSource() {
    return source;
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    final SqlWriter.Frame frame =
        writer.startList(SqlWriter.FrameTypeEnum.SELECT, "UPDATE", "");
    final int opLeft = getOperator().getLeftPrec();
    final int opRight = getOperator().getRightPrec();
    getTargetTable().unparse(writer, opLeft, opRight);
    SqlIdentifier alias = this.getAlias();
    if (alias != null) {
      writer.keyword("AS");
      alias.unparse(writer, opLeft, opRight);
    }
    final SqlWriter.Frame setFrame =
        writer.startList(SqlWriter.FrameTypeEnum.UPDATE_SET_LIST, "SET", "");
    for (Pair<SqlNode, SqlNode> pair
        : Pair.zip(getTargetColumnList(), getSourceExpressionList())) {
      writer.sep(",");
      SqlNode id = pair.left;
      id.unparse(writer, opLeft, opRight);
      writer.keyword("=");
      SqlNode sourceExp = pair.right;
      sourceExp.unparse(writer, opLeft, opRight);
    }

    if (source != null) {
      writer.newlineAndIndent();
      writer.sep("FROM");
      source.unparse(writer, opLeft, opRight);
    }

    writer.endList(setFrame);
    SqlNode condition = this.getCondition();
    if (condition != null) {
      writer.sep("WHERE");
      condition.unparse(writer, opLeft, opRight);
    }
    writer.endList(frame);
  }
}
