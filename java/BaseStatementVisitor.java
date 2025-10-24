package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
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
            processEntityForAccess(id, result);
        }
        for (SqlIdentifier sourceId : result.getSourceTables()) {
            processEntityForAccess(sourceId, result);
        }
    }

    private void processEntityForAccess(SqlIdentifier id, SqlNodeVisitor.Result result) {
        if (id == null) return;

        // Check if this is a CTE reference - skip creating entities for CTEs
        String tableName = getTableName(id);
        if (tableName != null && result.getContext().getCteAliases().contains(tableName)) {
            return;
        }

        relationshipExtractor.createAccess(id, userName, defaultDatabase, defaultSchema, result.getEntityRelationships());
    }

    private String getTableName(SqlIdentifier id) {
        if (id instanceof SqlTableIdentifier tableId) {
            return tableId.getTableName();
        } else if (id instanceof SqlViewIdentifier viewId) {
            return viewId.getViewName();
        } else if (id.names != null && !id.names.isEmpty()) {
            return id.names.get(id.names.size() - 1);
        }
        return null;
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

    @SuppressWarnings("unused")
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

