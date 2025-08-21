package com.calcite_new.sql.parser;

public class BigQuerySqlParserFixture extends SqlParserFixture {

  public BigQuerySqlParserFixture() {
    super(new BigQuerySqlParser(SqlParser.Mode.DEBUG));
  }

}

