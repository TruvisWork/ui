package com.calcite_new.core.model;

import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.Table;
import org.apache.calcite.DataContext;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.StructKind;
import org.apache.calcite.schema.impl.AbstractTable;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class ScannableTable extends AbstractTable implements org.apache.calcite.schema.ScannableTable {
  private final Table table;

  private ScannableTable(Table table) {
    this.table = table;
  }

  @Override
  public Enumerable<@Nullable Object[]> scan(DataContext root) {
    throw new UnsupportedOperationException("Scan not supported");
  }

  @Override
  public RelDataType getRowType(RelDataTypeFactory typeFactory) {
    List<String> names = table.getColumns().stream().map(c -> c.name().getName()).toList();
    List<RelDataType> types = table.getColumns().stream()
        .map(c -> {
          DataType type = c.type();
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

  public static ScannableTable create(Table table) {
    return new ScannableTable(table);
  }
}
