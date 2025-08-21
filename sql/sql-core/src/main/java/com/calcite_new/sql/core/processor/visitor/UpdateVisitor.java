package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlUpdate;

public class UpdateVisitor extends BaseStatementVisitor {

    public UpdateVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
    }

    @Override
    public SqlNodeVisitor.Result visit(SqlCall call) {
        SqlUpdate update = (SqlUpdate) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        result.setStatementType(StatementType.UPDATE);

        addAccess(update.getTargetTable(), result);

        if (update.getCondition() != null) {
            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result conditionResult = update.getCondition().accept(visitor);
            SqlNodeVisitor.mergeResults(result, conditionResult);
            addDependsOn(update.getTargetTable(), conditionResult, result);
        }

        return result;
    }
}
