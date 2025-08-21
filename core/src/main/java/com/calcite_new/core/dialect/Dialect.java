package com.calcite_new.core.dialect;

public abstract class Dialect {
  public final Product product;

  protected Dialect(Product product) {
    this.product = product;
  }

  public abstract String normalize(String name);

  public abstract int supportedQualificationLevels();
}
