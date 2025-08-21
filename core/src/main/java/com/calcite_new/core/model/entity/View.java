package com.calcite_new.core.model.entity;

import com.calcite_new.core.model.Identifier;

import java.util.List;

/**
 * Class representing a database view.
 */
public class View extends DatabaseEntity implements RelationalEntity {
  private final String sql;
  private final List<Column> columns;

  public View(List<Identifier> namespace, Identifier name, List<Column> columns, String sql, long createdTimestamp) {
    super(namespace.stream().toList(), name, createdTimestamp);
    this.columns = columns;
    this.sql = sql;
  }

  @Override
  public EntityKind getKind() {
    return EntityKind.VIEW;
  }

  public List<Column> getColumns() {
    return columns;
  }

  public String getSql() {
    return sql;
  }
}
