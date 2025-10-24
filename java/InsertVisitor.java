package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlInsert;
import org.apache.calcite.sql.SqlSelect;

import java.util.ArrayList;
import java.util.List;

public class InsertVisitor extends BaseStatementVisitor {

    private List<String> externalCteAliases = new ArrayList<>();

    public InsertVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
    }

    /**
     * Sets CTE aliases from parent WITH statement before processing
     */
    public void setCteAliases(List<String> cteAliases) {
        if (cteAliases != null) {
            this.externalCteAliases = new ArrayList<>(cteAliases);
        }
    }

    @Override
    public SqlNodeVisitor.Result visit(SqlCall call) {
        SqlInsert insert = (SqlInsert) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        result.setStatementType(StatementType.INSERT);

        // Copy external CTE aliases to result context
        for (String cteAlias : externalCteAliases) {
            result.getContext().addCteAlias(cteAlias);
        }

        addAccess(insert.getTargetTable(), result);

        if (insert.getSource() != null) {
            // If source is a SELECT, pass CTE aliases to it
            if (insert.getSource() instanceof SqlSelect selectSource) {
                SelectVisitor selectVisitor = new SelectVisitor(userName, defaultDatabase, defaultSchema, 
                                                                 relationshipExtractor, entityCatalog);
                selectVisitor.setCteAliases(result.getContext().getCteAliases());
                SqlNodeVisitor.Result sourceResult = selectVisitor.visit(selectSource);
                SqlNodeVisitor.mergeResults(result, sourceResult);
                addDependsOn(insert.getTargetTable(), sourceResult, result);
            } else {
                // For other source types, use general visitor
                SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, 
                                                             relationshipExtractor, entityCatalog);
                SqlNodeVisitor.Result sourceResult = insert.getSource().accept(visitor);
                
                // Copy CTE aliases to source result
                for (String cteAlias : result.getContext().getCteAliases()) {
                    sourceResult.getContext().addCteAlias(cteAlias);
                }
                
                SqlNodeVisitor.mergeResults(result, sourceResult);
                addDependsOn(insert.getTargetTable(), sourceResult, result);
            }
        }

        return result;
    }
}

