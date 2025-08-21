package com.calcite_new.sql.core.converter;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Join;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.util.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Map;
import java.util.stream.IntStream;

/**
 * JoinScope is a specialized scope for handling JOIN operations in SQL queries.
 * It extends the base Scope class and provides additional functionality for
 * managing the input relations and their aliases.
 */
public class JoinScope extends Scope {
  private final Join joinRel;
  // JoinScope is a specialized Scope for handling JOIN operations.
  // It can be used to manage the input relations and their aliases.
  // Inherits all methods from Scope, but can add join-specific logic if needed.
  public JoinScope(Scope parent, ImmutableList<CustomSqlToRelConverter.Frame> relations, RelNode joinRel, @Nullable Map<String, RelNode> cteMap) {
    super(parent, relations, cteMap, null, false, null, null, null, null, null);
    this.joinRel = (Join) joinRel;
  }

  @Override
  public @Nullable Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> findField(SqlIdentifier id) {
    Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> fieldInfo =  super.findField(id);
    if (fieldInfo != null) {
      CustomSqlToRelConverter.Frame frame = fieldInfo.left;
      RelDataTypeField field = fieldInfo.right;
      if (joinRel.getJoinType() == JoinRelType.LEFT && frame.offset != 0) {
        // Field was resolved from the right input of a left join
        int offset = frame.offset;
        fieldInfo = getUpdatedFieldInfo(frame, field, offset);
      } else if (joinRel.getJoinType() == JoinRelType.RIGHT && frame.offset == 0) {
        // Field was resolved from the left input of a right join
        fieldInfo = getUpdatedFieldInfo(frame, field, 0);
      }
    }
    return fieldInfo;
  }

  /**
   * Updates field type using join's RelDataType.
   * @param frame
   * @param field
   * @param offset
   * @return
   */
  private Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> getUpdatedFieldInfo(CustomSqlToRelConverter.Frame frame, RelDataTypeField field, int offset) {
    int index = IntStream.range(0, frame.rowType.getFieldCount())
        .filter(f -> frame.rowType.getFieldList().get(f).getName().equalsIgnoreCase(field.getName()))
        .findFirst().getAsInt();
    // Adjust the index
    RelDataType fieldType = joinRel.getRowType().getFieldList().get(index + offset).getType();
    // create new RelDataTypeField with updated type
    RelDataTypeField leftField = new RelDataTypeFieldImpl(field.getName(), index, fieldType);
    return Pair.of(frame, leftField);
  }

  @Override
  public RelNode getRelNode() {
    return joinRel;
  }
}