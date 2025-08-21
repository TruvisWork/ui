package com.calcite_new.core.dialect.etl;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.dialect.Product;

public abstract class EtlDialect extends Dialect {

  protected EtlDialect(Product product) {
    super(product);
  }

  @Override
  public int supportedQualificationLevels() {
    throw new UnsupportedOperationException("Qualification levels not supported for "
        + this.getClass().getName() + " dialect");
  }

}
