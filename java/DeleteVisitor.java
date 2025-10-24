package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.core.processor.utils.InClauseAnalyzer;
import com.calcite_new.sql.core.processor.utils.PartitionColumnFunctionDetector;
import com.calcite_new.sql.core.processor.utils.SqlConditionUtils;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.model.entity.context.clause.WhereClause;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlDelete;
import org.apache.calcite.sql.SqlNode;

import static com.calcite_new.sql.core.processor.utils.SqlConditionUtils.isConditionAlwaysTrue;

public class DeleteVisitor extends BaseStatementVisitor {

    private final PartitionColumnFunctionDetector partitionDetector;
    public DeleteVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
        this.partitionDetector = new PartitionColumnFunctionDetector(entityCatalog, defaultDatabase, defaultSchema);
    }
    @Override
    public SqlNodeVisitor.Result visit(SqlCall call) {
        SqlDelete delete = (SqlDelete) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        result.setStatementType(StatementType.DELETE);

        addAccess(delete.getTargetTable(), result);

        if (delete.getTargetTable() instanceof org.apache.calcite.sql.SqlIdentifier targetTableId) {
            partitionDetector.registerTable(targetTableId);
        }
        if (delete.getCondition() != null) {
            SqlNode deleteCondition = delete.getCondition();
            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result conditionResult = deleteCondition.accept(visitor);

            if (conditionResult == null) {
                conditionResult = new SqlNodeVisitor.Result();
            }
            SqlNodeVisitor.mergeResults(result, conditionResult);
            addDependsOn(delete.getTargetTable(), conditionResult, result);

            WhereClause whereClause = new WhereClause();
            whereClause.setHasTrueCondition(isConditionAlwaysTrue(deleteCondition));
            InClauseAnalyzer.InClauseInfo inInfo = InClauseAnalyzer.analyze(deleteCondition);
            whereClause.setHasInWithSubquery(inInfo.hasInWithSubquery());
            whereClause.setHasInWithConstant(inInfo.hasInWithConstant());
            whereClause.setHasCaseInsensitiveComparison(SqlConditionUtils.hasCaseInsensitiveComparison(deleteCondition));
            boolean hasFunctionOnPartition = partitionDetector.hasFunctionOnPartitionColumn(deleteCondition);
            whereClause.setHasFunctionOnPartitionColumn(hasFunctionOnPartition);
            result.getContext().setWhereClause(whereClause);
        }
        partitionDetector.clearRegistry();

        return result;
    }
}
