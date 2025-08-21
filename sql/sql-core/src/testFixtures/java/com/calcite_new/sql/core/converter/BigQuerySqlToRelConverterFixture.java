package com.calcite_new.sql.core.converter;

import com.calcite_new.core.catalog.BigQueryFoodmartCatalogBuilder;
import com.calcite_new.core.dialect.Product;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.parser.BigQuerySqlParserFixture;

import java.util.List;

/**
 * Fixture for testing SQL to Rel conversion using BigQuery dialect.
 */
public class BigQuerySqlToRelConverterFixture extends SqlToRelConverterFixture {
  private static final EntityCatalog catalog = new BigQueryFoodmartCatalogBuilder().build();
  private static final String PROJECT_NAME = "test";
  private static final String DATASET_NAME = "foodmart-test";

  static {
    new BigQueryFoodmartCatalogBuilder()
        .withCatalog(catalog)
        .withProjectName(PROJECT_NAME)
        .withDatasetName(DATASET_NAME)
        .build();
  }

  public BigQuerySqlToRelConverterFixture() {
    super(new BigQuerySqlDialect(),
        new BigQuerySqlParserFixture(),
//        List.of(Product.BIG_QUERY.name.toLowerCase(), PROJECT_NAME));
        List.of(Product.BIG_QUERY.name, PROJECT_NAME, DATASET_NAME));
    withCatalog(catalog);
  }

  public static SqlToRelConverterFixture with(String sql) {
    return new BigQuerySqlToRelConverterFixture().withSql(sql);
  }

}
