package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;

import java.util.List;

public abstract class BaseStatementVisitor implements StatementVisitor {

    protected final String userName;
    protected final String defaultDatabase;
    protected final String defaultSchema;
    protected final RelationshipExtractor relationshipExtractor;
    protected final EntityCatalog entityCatalog;

    public BaseStatementVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        this.userName = userName;
        this.defaultDatabase = defaultDatabase;
        this.defaultSchema = defaultSchema;
        this.relationshipExtractor = relationshipExtractor;
        this.entityCatalog = entityCatalog;
    }

    protected void addAccess(SqlNode node, SqlNodeVisitor.Result result) {
        SqlIdentifier id = extractTableIdentifier(node);
        if (id != null) {
            relationshipExtractor.createAccess(id, userName, defaultDatabase, defaultSchema, result.getEntityRelationships());
        }
        for (SqlIdentifier sourceId : result.getSourceTables()) {
            relationshipExtractor.createAccess(sourceId, userName, defaultDatabase, defaultSchema, result.getEntityRelationships());
        }
    }

    protected void addDependsOn(SqlNode targetTable, SqlNodeVisitor.Result fromResult, SqlNodeVisitor.Result mainResult) {
        SqlIdentifier targetId = extractTableIdentifier(targetTable);
        if (targetId == null) {
            return;
        }

        //removeIdentifier(fromResult.getSourceTables(), targetId);

        for (SqlIdentifier sourceId : fromResult.getSourceTables()) {
            SqlIdentifier actualSourceId = extractTableIdentifier(sourceId);
            if (actualSourceId != null) {
                relationshipExtractor.createDependsOn(targetId, actualSourceId, defaultDatabase, defaultSchema, mainResult.getEntityRelationships());
            }
        }
    }

    private void removeIdentifier(List<SqlIdentifier> identifiers, SqlIdentifier target) {
        String targetName = String.join(".", target.names);
        identifiers.removeIf(id -> String.join(".", id.names).equals(targetName));
    }

    private SqlIdentifier extractTableIdentifier(SqlNode node) {
        if (node instanceof SqlIdentifier id) {
            return id;
        } else if (node instanceof SqlBasicCall call &&
                call.getOperator().getName().equalsIgnoreCase("AS")) {
            SqlNode firstOperand = call.getOperandList().get(0);
            if (firstOperand instanceof SqlIdentifier) {
                return (SqlIdentifier) firstOperand;
            }
        }
        return null;
    }
}

