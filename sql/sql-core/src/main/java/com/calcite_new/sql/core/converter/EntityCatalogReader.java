package com.calcite_new.sql.core.converter;

import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.ScannableTable;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.RelationalEntity;
import com.calcite_new.core.resolver.EntityResolver;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.config.CalciteConnectionConfig;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.prepare.CalciteCatalogReader;
import org.apache.calcite.prepare.RelOptTableImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.sql.validate.SqlNameMatcher;
import org.apache.calcite.sql.validate.SqlNameMatchers;

import java.util.List;

public class EntityCatalogReader extends CalciteCatalogReader {
  private final RelOptCluster cluster;
  private final EntityResolver resolver;
  private final SqlDialect dialect;
  private final List<String> defaultQualifiers;

  private EntityCatalogReader(
      RelOptCluster cluster,
      EntityResolver resolver,
      SqlDialect dialect,
      List<String> defaultQualifiers,
      CalciteSchema rootSchema, RelDataTypeFactory typeFactory,
      CalciteConnectionConfig config) {
    super(rootSchema,
        defaultQualifiers, typeFactory,
        config);
    this.cluster = cluster;
    this.resolver = resolver;
    this.dialect = dialect;
    this.defaultQualifiers = defaultQualifiers;
  }

  public static EntityCatalogReader create(
      RelOptCluster cluster,
      EntityResolver resolver,
      SqlDialect dialect,
      List<String> defaultQualifiers) {
    return new EntityCatalogReader(cluster, resolver, dialect,
        defaultQualifiers, resolver.getSchema(), cluster.getTypeFactory(), CalciteConnectionConfig.DEFAULT);
  }


  @Override
  public RelOptTableImpl getTable(List<String> names) {
    String tableName = names.get(names.size() - 1);
    EntityQualifier qualifier = new EntityQualifier(names, defaultQualifiers, dialect);
    // Table scan
    DatabaseEntity entity = resolver.resolve(qualifier);
    if (!(entity instanceof RelationalEntity)) {
        throw new IllegalArgumentException("Expected a RelationalEntity but got " + entity.getClass().getSimpleName());
    }
    RelationalEntity relationalEntity = (RelationalEntity) entity;
    return createRelOptTable(relationalEntity);
  }

  private RelOptTableImpl createRelOptTable(RelationalEntity entity) {
    ImmutableList<String> qualifiedName = getQualifiedName(entity);
    ScannableTable scannableTable = ScannableTable.create(entity);
    RelDataType rowType = scannableTable.getRowType(cluster.getTypeFactory());
    return RelOptTableImpl.create(null, rowType, scannableTable, qualifiedName);
  }

  private static ImmutableList<String> getQualifiedName(RelationalEntity entity) {
    DatabaseEntity dbEntity = (DatabaseEntity) entity;
    List<String> namespace = dbEntity.getNamespace().stream().map(Identifier::getNormalizedName).toList();
    return ImmutableList.<String>builder()
        .addAll(namespace)
        .add(dbEntity.getName().getNormalizedName())
        .build();
  }

//  @Override
//  public RelDataType getNamedType(SqlIdentifier typeName) {
//    throw new UnsupportedOperationException("Not implemented");
//  }
//
//  @Override
//  public List<SqlMoniker> getAllSchemaObjectNames(List<String> names) {
//    throw new UnsupportedOperationException("Not implemented");
//  }

  @Override
  public List<List<String>> getSchemaPaths() {
    return List.of(defaultQualifiers);
  }
//
//  @Override
//  public @Nullable RelDataTypeField field(RelDataType rowType, String alias) {
//    throw new UnsupportedOperationException("Not implemented");
//  }

  @Override
  public SqlNameMatcher nameMatcher() {
    return SqlNameMatchers.withCaseSensitive(false);
  }

//  @Override
//  public boolean matches(String string, String name) {
//    throw new UnsupportedOperationException("Not implemented");
//  }
//
//  @Override
//  public RelDataType createTypeFromProjection(RelDataType type, List<String> columnNameList) {
//    throw new UnsupportedOperationException("Not implemented");
//  }
//
//  @Override
//  public boolean isCaseSensitive() {
//    throw new UnsupportedOperationException("Not implemented");
//  }

  @Override
  public CalciteSchema getRootSchema() {
    return resolver.getSchema();
  }
//
//  @Override
//  public CalciteConnectionConfig getConfig() {
//    throw new UnsupportedOperationException("Not implemented");
//  }
//
//  @Override
//  public <C> @Nullable C unwrap(Class<C> aClass) {
//    return null;
//  }
}
