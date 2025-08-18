package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlInsert;

public class InsertVisitor extends BaseStatementVisitor {

    public InsertVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
    }

    @Override
    public SqlNodeVisitor.Result visit(SqlCall call) {
        SqlInsert insert = (SqlInsert) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        result.setStatementType(StatementType.INSERT);

        addAccess(insert.getTargetTable(), result);

        if (insert.getSource() != null) {
            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result sourceResult = insert.getSource().accept(visitor);
            SqlNodeVisitor.mergeResults(result, sourceResult);
            addDependsOn(insert.getTargetTable(), sourceResult, result);
        }

        return result;
    }
}

