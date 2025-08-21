package com.calcite_new.core.model.entity;

import com.calcite_new.core.model.Identifier;

import java.util.List;

/**
 * Abstract class representing a database entity.
 * This class serves as a base for different types of entities such as tables, views, stored proc etc.
 */
public abstract class DatabaseEntity implements Entity {
  private final List<Identifier> namespace;
  private final Identifier name;
  private final long createdTimestamp;

  protected DatabaseEntity(List<Identifier> namespace, Identifier name, long createdTimestamp) {
    this.namespace = namespace;
    this.name = name;
    this.createdTimestamp = createdTimestamp;
  }

  public List<Identifier> getNamespace() {
    return namespace;
  }

  public Identifier getName() {
    return name;
  }

  public long getCreatedTimestamp() {
    return createdTimestamp;
  }

  public abstract EntityKind getKind();

}
