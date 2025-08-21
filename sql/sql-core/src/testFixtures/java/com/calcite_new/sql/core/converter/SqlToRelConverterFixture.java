package com.calcite_new.sql.core.converter;

import com.calcite_new.core.catalog.BigQueryFoodmartCatalogBuilder;
import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.parser.SqlParserFixture;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.SqlNode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Fixture for testing SQL to Rel conversion.
 *
 * <p>Subclasses should provide specific dialects and parser fixtures.
 */
public abstract class SqlToRelConverterFixture {
  protected final SqlDialect dialect;
  protected final SqlParserFixture parserFixture;
  protected List<String> defaultQualification;
  protected String sql;
  protected EntityCatalog catalog;
//  protected SqlToRelConverter converter;
//  protected SqlToRelConverterG converter;
//  protected SqlToRelConverter converter;
  protected CustomSqlToRelConverter converter;

  protected SqlToRelConverterFixture(
      SqlDialect dialect,
      SqlParserFixture parserFixture,
      List<String> defaultQualification) {
    this.dialect = dialect;
    this.parserFixture = parserFixture;
    this.defaultQualification = defaultQualification;
  }

  public SqlToRelConverterFixture withSql(String sql) {
    this.sql = sql;
    return this;
  }

  public SqlToRelConverterFixture withCatalog(EntityCatalog catalog) {
    this.catalog = catalog;
    return this;
  }

  public SqlToRelConverterFixture withDefaultQualification(List<String> defaultQualification) {
    this.defaultQualification = defaultQualification;
    return this;
  }

  public void check(String expectedRelPlan) {
    RelNode rel = toRel();
    assertEquals(expectedRelPlan, rel.explain());
  }

  public RelNode toRel() {
    assertNotNull(sql, "SQL not provided.");
    assertNotNull(catalog, "Catalog not provided.");

    if (converter == null) {
      init();
    }
    SqlNode sqlNode = parserFixture.withSql(sql).parse();
//    assert converter.validator != null;
//    SqlNode validatedNode = converter.validator.validate(sqlNode);
//    RelNode rel = converter.convertQuery(validatedNode, false, true).rel;
//    return rel;

    return converter.convert(sqlNode);
  }

  private void init() {
    EntityCatalog catalog = this.catalog == null ? new BigQueryFoodmartCatalogBuilder().build() : this.catalog;
    EntityResolver entityResolver = new EntityResolver(catalog);
    VolcanoPlanner planner = new VolcanoPlanner();
    RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
    RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
    EntityCatalogReader catalogReader =
        EntityCatalogReader.create(cluster, entityResolver, dialect, defaultQualification);
    this.converter = new CustomSqlToRelConverter(
        cluster,
        catalogReader,
        defaultQualification,
        dialect
    );


//    // Initialize SqlToRelConverter
////    this.converter = new SqlToRelConverter(cluster, entityResolver, new BigQuerySqlDialect(), defaultQualification);
//    SqlOperatorTable operatorTable = SqlStdOperatorTable.instance();
//    RelDataTypeFactory typeFactory = cluster.getTypeFactory();
//    SqlValidator.Config config = SqlValidator.Config.DEFAULT.withIdentifierExpansion(true);
////    Prepare.CatalogReader cr = new CalciteCatalogReader(entityResolver.getSchema(),
////        defaultQualification, typeFactory, CalciteConnectionConfig.DEFAULT);
//    SqlValidator sqlValidator =
//        new SqlValidatorImpl(operatorTable, catalogReader, typeFactory, config, dialect);
////        new TFSqlValidator(operatorTable, catalogReader, typeFactory, config, dialect);
////        SqlValidatorUtil.newValidator(operatorTable, catalogReader, typeFactory, config);
//    RelOptTable.ViewExpander viewExpander = new ViewExpanderImpl();
//    SqlRexConvertletTable convertletTable = StandardConvertletTable.INSTANCE;
//
//    SqlToRelConverter.Config conf = SqlToRelConverter.CONFIG;
//    this.converter = new SqlToRelConverter(
//        viewExpander,
//        sqlValidator,
//        catalogReader,
//        cluster,
//        convertletTable,
//        conf);
  }

}
