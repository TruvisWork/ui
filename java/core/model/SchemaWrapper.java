package com.calcite_new.core.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.SchemaVersion;
import org.apache.calcite.schema.Table;
import org.apache.calcite.sql.type.SqlTypeName;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class SchemaWrapper extends CalciteSchema {

  public SchemaWrapper(Schema schema) {
    super(null, schema, "", null, null, null, null,
        null, null, null, List.of());
  }

  public SchemaWrapper(Schema schema, String name) {
    super(null, schema, name, null, null, null, null,
        null, null, null, List.of());
  }

  @Override
  protected @Nullable CalciteSchema getImplicitSubSchema(String schemaName, boolean caseSensitive) {
    Namespace subSchema = (Namespace) schema.getSubSchema(schemaName);
    return subSchema != null ? new SchemaWrapper(subSchema, subSchema.getName().getNormalizedName()) : null;
  }

  @Override
  protected @Nullable TableEntry getImplicitTable(String tableName, boolean caseSensitive) {
    Table table = schema.getTable(tableName);
    assert table != null;
    return tableEntry(tableName, table);
  }

  @Override
  protected @Nullable TypeEntry getImplicitType(String name, boolean caseSensitive) {
    return new TypeEntryImpl(this, name, factory -> factory.createSqlType(SqlTypeName.ANY));
//    throw new UnsupportedOperationException("Implicit type resolution is not supported");
  }

  @Override
  protected @Nullable TableEntry getImplicitTableBasedOnNullaryFunction(String tableName, boolean caseSensitive) {
    throw new UnsupportedOperationException("Implicit table based on nullary function resolution is not supported");
  }

  @Override
  protected void addImplicitSubSchemaToBuilder(ImmutableSortedMap.Builder<String, CalciteSchema> builder) {

  }

  @Override
  protected void addImplicitTableToBuilder(ImmutableSortedSet.Builder<String> builder) {

  }

  @Override
  protected void addImplicitFunctionsToBuilder(ImmutableList.Builder<Function> builder, String name, boolean caseSensitive) {

  }

  @Override
  protected void addImplicitFuncNamesToBuilder(ImmutableSortedSet.Builder<String> builder) {

  }

  @Override
  protected void addImplicitTypeNamesToBuilder(ImmutableSortedSet.Builder<String> builder) {

  }

  @Override
  protected void addImplicitTablesBasedOnNullaryFunctionsToBuilder(ImmutableSortedMap.Builder<String, Table> builder) {

  }

  @Override
  protected CalciteSchema snapshot(@Nullable CalciteSchema parent, SchemaVersion version) {
    throw new UnsupportedOperationException("Snapshot is not supported");
  }

  @Override
  protected boolean isCacheEnabled() {
    return false;
  }

  @Override
  public void setCache(boolean cache) {

  }

  @Override
  public CalciteSchema add(String name, Schema schema) {
    throw new UnsupportedOperationException("Add schema is not supported");
  }
}
