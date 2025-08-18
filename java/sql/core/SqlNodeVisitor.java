package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.model.entity.EntityRelationship;
import com.calcite_new.sql.model.entity.StatementContext;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import lombok.Getter;
import lombok.Setter;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.util.SqlBasicVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SqlNodeVisitor extends SqlBasicVisitor<SqlNodeVisitor.Result> {

    @Getter
    @Setter
    public static class Result {
        private StatementType statementType;
        private final List<EntityRelationship> entityRelationships = new ArrayList<>();
        private final StatementContext context = new StatementContext();
        private final List<SqlIdentifier> sourceTables = new ArrayList<>();
    }

    private final Map<Class<? extends SqlCall>, StatementVisitor> statementVisitors;


    public SqlNodeVisitor(String user, String database, String schema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {

        this.statementVisitors = Map.of(
                SqlSelect.class, new SelectVisitor(user, database, schema, relationshipExtractor, entityCatalog),
                SqlInsert.class, new InsertVisitor(user, database, schema, relationshipExtractor, entityCatalog),
                com.calcite_new.sql.SqlUpdate.class, new UpdateVisitor(user, database, schema, relationshipExtractor, entityCatalog),
                SqlDelete.class, new DeleteVisitor(user, database, schema, relationshipExtractor, entityCatalog)
        );
    }

    @Override
    public Result visit(SqlCall call) {
        if (call == null) {
            return new Result();
        }

        StatementVisitor visitor = statementVisitors.get(call.getClass());
        if (visitor != null) {
            return visitor.visit(call);
        }

        return visitGenericCall(call);
    }

    @Override
    public Result visit(SqlIdentifier id) {
        // Avoid adding source tables or relationships for column refs
        return new Result();
    }

    private Result visitGenericCall(SqlCall call) {
        Result result = new Result();
        for (SqlNode operand : call.getOperandList()) {
            if (operand != null) {
                Result subResult = operand.accept(this);
                mergeResults(result, subResult);
            }
        }
        return result;
    }

    public static void mergeResults(Result main, Result other) {
        if (other == null) return;
        if (main.entityRelationships != other.entityRelationships) {
            other.getEntityRelationships().stream()
                    .filter(relationship -> !main.getEntityRelationships().contains(relationship))
                    .forEach(main.getEntityRelationships()::add);
        }
        main.getSourceTables().addAll(other.getSourceTables());
        if (other.getContext() != null) {
            main.getContext().merge(other.getContext());
        }
    }
}

