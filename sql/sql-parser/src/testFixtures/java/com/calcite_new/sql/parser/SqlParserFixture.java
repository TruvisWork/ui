package com.calcite_new.sql.parser;

import org.apache.calcite.sql.SqlDialect;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.dialect.CalciteSqlDialect;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fixture for testing SQL parsing.
 *
 * <p>Subclasses should provide specific dialects and parser implementations.
 */
public abstract class SqlParserFixture {
  protected static SqlDialect CALCITE_DIALECT = new CalciteSqlDialect(SqlDialect.EMPTY_CONTEXT
      .withDatabaseProduct(SqlDialect.DatabaseProduct.CALCITE));

  private final SqlParser parser;
  protected String sql;

  public SqlParserFixture(SqlParser parser) {
    this.parser = parser;
  }

  public SqlParserFixture withSql(String sql) {
    this.sql = sql;
    return this;
  }

  public void check(String expectedSql) {
    Objects.requireNonNull(sql, "SQL string is null");
    SqlNode node = parse();
    String actualSql = node.toSqlString(CALCITE_DIALECT).toString();
    assertEquals(expectedSql, actualSql);
  }

  public SqlNode parse() {
    SqlNode node = parser.parse(sql);
    if (!parser.success()) {
      throw new AssertionError("Parsing failed");
    }
    return node;
  }

}
