package com.calcite_new.core.dialect.sql;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.dialect.Product;

public abstract class SqlDialect extends Dialect {

  protected SqlDialect(Product product) {
    super(product);
  }

  protected abstract String identifierQuote();

}
