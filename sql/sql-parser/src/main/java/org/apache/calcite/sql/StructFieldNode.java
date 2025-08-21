package org.apache.calcite.sql;

import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlVisitor;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.calcite.util.Litmus;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Custom node class to represent a struct field with name and type.
 */
public class StructFieldNode extends SqlNode {
  private final SqlIdentifier name;
  private final SqlDataTypeSpec type;

  public StructFieldNode(SqlIdentifier name, SqlDataTypeSpec type, SqlParserPos pos) {
    super(pos);
    this.name = name;
    this.type = type;
  }

  public SqlIdentifier getName() {
    return name;
  }

  public SqlDataTypeSpec getType() {
    return type;
  }

  @Override
  public SqlNode clone(SqlParserPos pos) {
    return new StructFieldNode(
        (SqlIdentifier) name.clone(pos),
        (SqlDataTypeSpec) type.clone(pos),
        pos);
  }

  @Override
  public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
    name.unparse(writer, leftPrec, rightPrec);
    writer.print(" ");
    type.unparse(writer, leftPrec, rightPrec);
  }

  @Override
  public void validate(SqlValidator validator, SqlValidatorScope scope) {

  }

  @Override
  public <R> R accept(SqlVisitor<R> visitor) {
    return null;
  }

  @Override
  public boolean equalsDeep(@Nullable SqlNode node, Litmus litmus) {
    return false;
  }
}
