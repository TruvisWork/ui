package com.calcite_new.sql.core;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rel.type.RelDataType;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class ViewExpanderImpl implements RelOptTable.ViewExpander {
  @Override
  public RelRoot expandView(RelDataType rowType, String queryString, List<String> schemaPath, @Nullable List<String> viewPath) {
    throw  new UnsupportedOperationException("View expansion is not supported");
  }
}
