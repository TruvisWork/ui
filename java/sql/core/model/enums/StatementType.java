package com.calcite_new.sql.model.enums;

import java.util.List;

/**
 * Enum representing different types of SQL statements.
 */
public enum StatementType {
    INSERT(StatementTypeFamily.DML),
    UPDATE(StatementTypeFamily.DML),
    DELETE(StatementTypeFamily.DML),
    MERGE(StatementTypeFamily.DML),
    SELECT(StatementTypeFamily.DRL),
    CTAS(StatementTypeFamily.DDL),
    CVAS(StatementTypeFamily.DDL),
    CREATE_TABLE(StatementTypeFamily.DDL),
    CREATE_VIEW(StatementTypeFamily.DDL),
    IGNORE(StatementTypeFamily.OTHER);

    public static List<StatementType> DMLS = List.of(INSERT, UPDATE, DELETE, MERGE);
    public static List<StatementType> DDLS = List.of(CTAS, CVAS, CREATE_TABLE, CREATE_VIEW);

    public final StatementTypeFamily family;

    StatementType(StatementTypeFamily family) {
        this.family = family;
    }

}
