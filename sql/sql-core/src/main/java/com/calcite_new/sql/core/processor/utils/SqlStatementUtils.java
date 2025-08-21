package com.calcite_new.sql.core.processor.utils;

import java.util.Set;

public class SqlStatementUtils {

    public static final Set<String> IGNORED_STATEMENTS = Set.of(
            "ANALYZE", "EXPLAIN", "SHOW", "DESCRIBE"
    );

    private SqlStatementUtils() {
    }

    public static boolean isIgnored(String statementKind) {
        return IGNORED_STATEMENTS.contains(statementKind);
    }
}

