package com.calcite_new.sql;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.List;

public class SqlCreateTable extends SqlCreate {
    private final SqlIdentifier name;
    private final SqlNodeList columnList;
    private final SqlNode query;

    public SqlCreateTable(SqlParserPos pos, boolean replace, boolean ifNotExists,
                          SqlIdentifier name, SqlNodeList columnList, SqlNode query) {
        super(new SqlSpecialOperator("CREATE TABLE", SqlKind.CREATE_TABLE), pos, replace, ifNotExists);
        this.name = name;
        this.columnList = columnList;
        this.query = query;
    }

    @Override
    public SqlOperator getOperator() {
        return new SqlSpecialOperator("CREATE TABLE", SqlKind.CREATE_TABLE);
    }

    @Override
    public List<SqlNode> getOperandList() {
        return List.of(name, columnList, query);
    }

    @Override
    public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
        writer.keyword("CREATE");
        if (getReplace()) writer.keyword("OR REPLACE");
        writer.keyword("TABLE");
        if (ifNotExists) writer.keyword("IF NOT EXISTS");
        name.unparse(writer, 0, 0);
        if (columnList != null && !columnList.isEmpty()) {
            columnList.unparse(writer, 0, 0);
        }
        if (query != null) {
            writer.keyword("AS\n");
            query.unparse(writer, 0, 0);
        }
    }

    public SqlIdentifier getName() { return name; }
    public SqlNodeList getColumnList() { return columnList; }
    public SqlNode getQuery() { return query; }
}