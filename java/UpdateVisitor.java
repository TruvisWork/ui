package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.core.processor.utils.PartitionColumnFunctionDetector;
import com.calcite_new.sql.model.entity.context.clause.WhereClause;
import org.apache.calcite.sql.SqlIdentifier;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlUpdate;

public class UpdateVisitor extends BaseStatementVisitor {

    private final PartitionColumnFunctionDetector partitionDetector;

    public UpdateVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
        this.partitionDetector = new PartitionColumnFunctionDetector(entityCatalog, defaultDatabase, defaultSchema);
    }

    @Override
    public SqlNodeVisitor.Result visit(SqlCall call) {
        SqlUpdate update = (SqlUpdate) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        result.setStatementType(StatementType.UPDATE);

        addAccess(update.getTargetTable(), result);

        if (update.getTargetTable() instanceof SqlIdentifier targetTableId) {
            partitionDetector.registerTable(targetTableId);
        }
        if (update.getCondition() != null) {
            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result conditionResult = update.getCondition().accept(visitor);
            SqlNodeVisitor.mergeResults(result, conditionResult);
            addDependsOn(update.getTargetTable(), conditionResult, result);
            WhereClause whereClause = new WhereClause();
            boolean hasFunctionOnPartition = partitionDetector.hasFunctionOnPartitionColumn(update.getCondition());
            whereClause.setHasFunctionOnPartitionColumn(hasFunctionOnPartition);
            result.getContext().setWhereClause(whereClause);
        }
        partitionDetector.clearRegistry();

        return result;
    }
}
