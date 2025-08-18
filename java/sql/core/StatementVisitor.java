package com.calcite_new.sql.core.processor.visitor;

import org.apache.calcite.sql.SqlCall;

public interface StatementVisitor {
    SqlNodeVisitor.Result visit(SqlCall call);
}
