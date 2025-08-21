package com.calcite_new.core.model;

import com.calcite_new.core.dialect.Dialect;

public class Identifier {
  private final String name;
  private final Dialect dialect;

  private Identifier(String name, Dialect dialect) {
    this.name = name;
    this.dialect = dialect;
  }

  public String getName() {
    return name;
  }

  public String getNormalizedName() {
    return dialect.normalize(name);
  }

  @Override
  public String toString() {
    return getNormalizedName();
  }

  public static Identifier of(String name, Dialect dialect) {
    return new Identifier(name, dialect);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Identifier that = (Identifier) o;

    return this.getNormalizedName().equals(that.getNormalizedName());
  }

  @Override
  public int hashCode() {
    return getNormalizedName().hashCode();
  }
}
