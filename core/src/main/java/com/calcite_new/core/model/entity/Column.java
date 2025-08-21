package com.calcite_new.core.model.entity;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.model.Identifier;

public record Column(Identifier name, int ordinalPosition, DataType type, boolean nullable) {

  public Column(String name, Dialect dialect, int ordinalPosition, DataType type, boolean nullable) {
    this(Identifier.of(name, dialect), ordinalPosition, type, nullable);
  }

  public Column(String name, Dialect dialect, int ordinalPosition, DataType type) {
    this(name, dialect, ordinalPosition, type, true);
  }

  public Column(Identifier name, int ordinalPosition, DataType type) {
    this(name, ordinalPosition, type, true);
  }
}
