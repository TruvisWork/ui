package com.calcite_new.sql.core.processor;

import lombok.Getter;

@Getter
public class DefaultQualifiers {

    String database;
    String schema;
    String userName;

    public DefaultQualifiers(String database, String schema, String userName) {
        this.database = database;
        this.schema = schema;
        this.userName = userName;
    }
}
