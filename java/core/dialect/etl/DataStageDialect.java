package com.calcite_new.core.dialect.etl;


import com.calcite_new.core.dialect.Product;

public class DataStageDialect extends EtlDialect {

  public DataStageDialect() {
    super(Product.DATASTAGE);
  }

  @Override
  public String normalize(String name) {
    throw new UnsupportedOperationException("Normalization not supported for DataStage dialect");
  }
}
