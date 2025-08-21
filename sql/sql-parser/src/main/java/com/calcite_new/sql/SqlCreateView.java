package com.calcite_new.sql;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.List;

public class SqlCreateView extends SqlCreate {
    private final SqlIdentifier name;
    private final SqlNodeList columnList;
    private final SqlNode query;

    public SqlCreateView(SqlParserPos pos, boolean replace, boolean ifNotExists,
                         SqlIdentifier name, SqlNodeList columnList, SqlNode query) {
        super(new SqlSpecialOperator("CREATE VIEW", SqlKind.CREATE_VIEW), pos, replace, ifNotExists);
        this.name = name;
        this.columnList = columnList;
        this.query = query;
    }

    @Override
    public SqlOperator getOperator() {
        return new SqlSpecialOperator("CREATE VIEW", SqlKind.CREATE_VIEW);
    }

    @Override
    public List<SqlNode> getOperandList() {
        return List.of(name, columnList, query);
    }

    @Override
    public void unparse(SqlWriter writer, int leftPrec, int rightPrec) {
        writer.keyword("CREATE");
        if (getReplace()) writer.keyword("OR REPLACE");
        writer.keyword("VIEW");
        if (ifNotExists) writer.keyword("IF NOT EXISTS");
        name.unparse(writer, 0, 0);
        if (columnList != null && !columnList.isEmpty()) {
            writer.print("(");
            columnList.unparse(writer, 0, 0);
            writer.print(")");
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