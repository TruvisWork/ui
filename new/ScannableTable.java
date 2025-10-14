package com.calcite_new.core.model;

import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.RelationalEntity;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.StructKind;
import org.apache.calcite.schema.impl.AbstractTable;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class ScannableTable extends AbstractTable implements org.apache.calcite.schema.ScannableTable {
  private final RelationalEntity entity;

  private ScannableTable(RelationalEntity entity) {
    this.entity = entity;
  }

  @Override
  public Enumerable<@Nullable Object[]> scan(DataContext root) {
    throw new UnsupportedOperationException("Scan not supported");
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = entity.getColumns().stream().map(c -> c.getName().getNormalizedName()).collect(Collectors.toList());
    List<RelDataType> types = entity.getColumns().stream()
        .map(c -> {
          DataType type = c.getType();
          if (type.getName().allowsPrec() && type.getName().allowsScale()) {
            return typeFactory.createSqlType(type.getName(), type.getPrecision(), type.getScale());
          }
          if (type.getName().allowsPrec()) {
            return typeFactory.createSqlType(type.getName(), type.getPrecision());
          }
          return typeFactory.createSqlType(type.getName());
        }).toList();
    return typeFactory.createStructType(StructKind.FULLY_QUALIFIED, types, names);
  }

  public static ScannableTable create(RelationalEntity entity) {
    return new ScannableTable(entity);
  }
}
