package com.calcite_new.core.dialect;

public enum Product {
  BIG_QUERY("BIG_QUERY"),
  DATASTAGE("DATASTAGE"),;

  public final String name;

  Product(String name) {
    this.name = name;
  }

  static Product get(String productName) {
    for (Product product : Product.values()) {
      if (product.name.equals(productName)) {
        return product;
      }
    }
    throw new IllegalArgumentException("Unknown product name: " + productName);
  }
}
