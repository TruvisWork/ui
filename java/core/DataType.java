package com.calcite_new.core.model.entity;

import org.apache.calcite.sql.type.SqlTypeName;

/**
 * Represents a data type in the system, encapsulating its SQL type name,
 * precision, scale, and nullability.
 */
public class DataType {
  private final SqlTypeName name;
  private final int precision;
  private final int scale;
  private final boolean nullable;

  private DataType(SqlTypeName name, int precision, int scale, boolean nullable) {
    this.name = name;
    this.precision = name.allowsPrec() ? precision : -1;
    this.scale = name.allowsScale() ? scale : -1;
    this.nullable = nullable;
  }

  public SqlTypeName getName() {
    return name;
  }

  public int getPrecision() {
    return precision;
  }

  public int getScale() {
    return scale;
  }

  public boolean isNullable() {
    return nullable;
  }

  public static DataType create(SqlTypeName name, int precision, int scale) {
    return create(name, precision, scale, true);
  }

  public static DataType create(SqlTypeName name, int precision, int scale, boolean nullable) {
    assert name.allowsPrec() && name.allowsScale() : "Expected DataType with precision and scale";
    return new DataType(name, precision, scale, nullable);
  }

  public static DataType create(SqlTypeName name, int precision) {
    return create(name, precision, true);
  }

  public static DataType create(SqlTypeName name, int precision, boolean nullable) {
    assert name.allowsPrec() && !name.allowsScale() : "Expected DataType with precision but no scale";
    return new DataType(name, precision, -1, nullable);
  }

  public static DataType create(SqlTypeName name) {
    return create(name, true);
  }

  public static DataType create(SqlTypeName name, boolean nullable) {
    assert !name.allowsPrec() && !name.allowsScale() : "Expected DataType without precision or scale";
    return new DataType(name, -1, -1, nullable);
  }
}
