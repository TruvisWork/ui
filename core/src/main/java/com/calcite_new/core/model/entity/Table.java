package com.calcite_new.core.model.entity;

import com.calcite_new.core.model.Identifier;

import java.util.List;

/**
 * Class representing a database table.
 */
public class Table extends DatabaseEntity implements RelationalEntity {
  private final List<Column> columns;

  public Table(List<Identifier> namespace, Identifier name, List<Column> columns, long createdTimestamp) {
    super(namespace.stream().toList(), name, createdTimestamp);
    this.columns = columns;
  }

  @Override
  public EntityKind getKind() {
    return EntityKind.TABLE;
  }

  public List<Column> getColumns() {
    return columns;
  }
}
