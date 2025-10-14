package com.calcite_new.core.model.entity;

import com.calcite_new.core.model.Identifier;

import java.util.List;

/**
 * Class representing a database table.
 */
public class Table extends DatabaseEntity implements RelationalEntity {
  private final List<Column> columns;
  private final List<String> clusteredColumns;
  private final List<String> partitionedColumns;

  public Table(List<Identifier> namespace, Identifier name, List<Column> columns, long createdTimestamp) {
    this(namespace, name, columns, createdTimestamp, null, null);
  }

  public Table(List<Identifier> namespace, Identifier name, List<Column> columns, long createdTimestamp,
               List<String> clusteredColumns, List<String> partitionedColumns) {
    super(namespace.stream().toList(), name, createdTimestamp);
    this.columns = columns;
    this.clusteredColumns = clusteredColumns != null ? List.copyOf(clusteredColumns) : List.of();
    this.partitionedColumns = partitionedColumns != null ? List.copyOf(partitionedColumns) : List.of();
  }

  @Override
  public EntityKind getKind() {
    return EntityKind.TABLE;
  }

  public List<Column> getColumns() {
    return columns;
  }

  public List<String> getClusteredColumns() {
    return clusteredColumns;
  }

  public List<String> getPartitionedColumns() {
    return partitionedColumns;
  }
}
