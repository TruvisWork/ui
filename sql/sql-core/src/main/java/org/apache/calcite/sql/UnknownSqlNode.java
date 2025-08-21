package org.apache.calcite.sql;

import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Litmus;
import org.checkerframework.checker.nullness.qual.Nullable;

public class UnknownSqlNode extends SqlNode {
  private final String token;

  private UnknownSqlNode(SqlParserPos pos, String token) {
    super(pos);
    this.token = token;
  }

  public static UnknownSqlNode create(SqlParserPos pos, String token) {
    return new UnknownSqlNode(pos, token);
  }

  @Override
  public SqlNode clone(SqlParserPos pos) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    writer.keyword(token);
  }

  @Override
  public void validate(SqlValidator validator, SqlValidatorScope scope) {

  }

  @Override
  public <R> R accept(SqlVisitor<R> visitor) {
    return visitor.visitNode(this);
  }

  @Override
  public boolean equalsDeep(@Nullable SqlNode node, Litmus litmus) {
    return false;
  }
}
