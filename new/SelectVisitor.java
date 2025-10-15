package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.dialect.Dialect;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.sql.core.processor.utils.InClauseAnalyzer;
import com.calcite_new.sql.core.processor.utils.JoinAnalyzer;
import com.calcite_new.sql.core.processor.utils.SqlConditionUtils;
import com.calcite_new.sql.model.entity.context.clause.*;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.model.entity.context.clause.SelectClause;
import com.calcite_new.sql.model.entity.context.clause.WhereClause;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCase;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SelectVisitor extends BaseStatementVisitor {

    private final JoinAnalyzer joinAnalyzer;
    private final Dialect dialect;
    public SelectVisitor(String userName, String defaultDatabase, String defaultSchema, RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
        super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
        this.joinAnalyzer = new JoinAnalyzer(entityCatalog);
        this.dialect = new BigQuerySqlDialect();
    }

    private SqlNodeVisitor.Result currentResult;
    private List<String> externalCteAliases = new ArrayList<>();

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
        SqlSelect select = (SqlSelect) call;
        SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
        this.currentResult = result; // Store current result for CTE checking
        
        // Copy external CTE aliases to result context
        for (String cteAlias : externalCteAliases) {
            result.getContext().addCteAlias(cteAlias);
        }
        
        result.setStatementType(StatementType.SELECT);

        SelectClause selectClause = new SelectClause();
        selectClause.setHasSelectAll(isSelectAll(select));
        selectClause.setHasDistinct(select.isDistinct());
        result.getContext().setSelectClause(selectClause);

        if (select.getFrom() != null) {
            var from = select.getFrom();

            if (from instanceof SqlBasicCall basicCall) {
                if (!basicCall.getOperandList().isEmpty() && basicCall.getOperandList().get(0) instanceof SqlOrderBy) {
                    OrderByClause orderByClause = new OrderByClause();
                    orderByClause.setIsOderByInsideSubQuery(true);
                    result.getContext().setOrderByClause(orderByClause);
                } else if (basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                        basicCall.getOperandList().get(0) instanceof SqlIdentifier tableId) {
                    // Skip CTEs from being added as source tables
                    String tableName = getTableName(tableId);
                    if (tableName == null || !result.getContext().getCteAliases().contains(tableName)) {
                        result.getSourceTables().add(tableId);
                    }
                }
            } else if (from instanceof SqlIdentifier id) {
                // Skip CTEs from being added as source tables
                String tableName = getTableName(id);
                if (tableName == null || !result.getContext().getCteAliases().contains(tableName)) {
                    result.getSourceTables().add(id);
                }
            } else if (from instanceof SqlJoin join) {
                processJoin(join, result);
            }

            // Process other from clause aspects
            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result fromResult = from.accept(visitor);
            SqlNodeVisitor.mergeResults(result, fromResult);
            addAccess(from, result);
        }

        // Initialize WhereClause regardless of WHERE condition existence
        WhereClause whereClause = new WhereClause();
        whereClause.setHasWhereClause(select.getWhere() != null);

        // Check SELECT list for case-insensitive comparisons
        boolean hasCaseInsensitive = false;
        for (SqlNode node : select.getSelectList()) {
            if (node instanceof SqlCase caseCall) {
                hasCaseInsensitive |= checkCaseExpressionForInsensitiveComparison(caseCall);
            } else if (node instanceof SqlBasicCall basicCall && basicCall.getOperator().getName().equalsIgnoreCase("AS")) {
                // Check if the first operand is a CASE expression
                SqlNode firstOperand = basicCall.getOperandList().get(0);
                if (firstOperand instanceof SqlCase caseCall) {
                    hasCaseInsensitive |= checkCaseExpressionForInsensitiveComparison(caseCall);
                }
            }
        }

        // If WHERE exists, check it too
        if (select.getWhere() != null) {
            SqlNode whereExpr = select.getWhere();
            InClauseAnalyzer.InClauseInfo inInfo = InClauseAnalyzer.analyze(whereExpr);
            whereClause.setHasInWithSubquery(inInfo.hasInWithSubquery());
            whereClause.setHasInWithConstant(inInfo.hasInWithConstant());
            hasCaseInsensitive |= SqlConditionUtils.hasCaseInsensitiveComparison(whereExpr);

            SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
            SqlNodeVisitor.Result whereResult = whereExpr.accept(visitor);
            SqlNodeVisitor.mergeResults(result, whereResult);
        }

        whereClause.setHasCaseInsensitiveComparison(hasCaseInsensitive);
        result.getContext().setWhereClause(whereClause);
        return result;
    }


    private static boolean isSelectAll(SqlSelect select) {
        return select.getSelectList().size() == 1 && "*".equals(select.getSelectList().get(0).toString());
    }

    private void processJoin(SqlJoin join, SqlNodeVisitor.Result result) {
        // Register tables with JoinAnalyzer
        SqlNode leftTable;
        if (join.getLeft() instanceof SqlJoin leftJoin) {
            processJoin(leftJoin, result);
            leftTable = leftJoin.getRight(); // For a nested join, use its right table as our left table
        } else {
            registerJoinTables(join.getLeft());
            leftTable = join.getLeft();
        }
        registerJoinTables(join.getRight());

        if (join.getCondition() != null) {
            JoinClause joinClause = new JoinClause();
            joinClause.setJoinType(join.getJoinType());
            joinClause.setJoinCondition(join.getCondition().toString());
            joinClause.setHasJoinOnStringColumn(joinAnalyzer.isJoinOnStringColumn(join.getCondition()));

            if (leftTable instanceof SqlIdentifier leftId) {
                joinClause.setLeftTable(leftId.toString());
            } else if (leftTable instanceof SqlBasicCall leftCall &&
                    !leftCall.getOperandList().isEmpty() && leftCall.getOperandList().get(0) instanceof SqlIdentifier leftId) {
                joinClause.setLeftTable(leftId.toString()); // Handle "AS" aliases
            }

            if (join.getRight() instanceof SqlIdentifier rightId) {
                joinClause.setRightTable(rightId.toString());
            } else if (join.getRight() instanceof SqlBasicCall rightCall &&
                    !rightCall.getOperandList().isEmpty() && rightCall.getOperandList().get(0) instanceof SqlIdentifier rightId) {
                joinClause.setRightTable(rightId.toString()); // Handle "AS" aliases
            }
            result.getContext().getJoinClauses().add(joinClause);
        }

        addSourceTable(join.getLeft(), result);
        addSourceTable(join.getRight(), result);
    }

    private void registerJoinTables(SqlNode node) {
        if (node instanceof SqlIdentifier id) {
            List<String> qualifiers = new ArrayList<>(id.names);
            String alias = id.names.get(id.names.size() - 1);
            
            // Skip if this is a CTE reference
            if (currentResult != null && currentResult.getContext().getCteAliases().contains(alias)) {
                return;
            }
            
            EntityQualifier qualifier = new EntityQualifier(qualifiers, List.of(defaultDatabase, defaultSchema), dialect);
            System.out.println("Entity Qualifier[if]: "+qualifier);
            joinAnalyzer.registerTableAlias(alias, qualifier);
        } else if (node instanceof SqlBasicCall call &&
                call.getOperator().getName().equalsIgnoreCase("AS")) {
            if (call.getOperandList().get(0) instanceof SqlIdentifier tableId &&
                    call.getOperandList().get(1) instanceof SqlIdentifier aliasId) {
                String tableName = tableId.names.get(tableId.names.size() - 1);
                
                // Skip if this is a CTE reference
                if (currentResult != null && currentResult.getContext().getCteAliases().contains(tableName)) {
                    return;
                }
                
                List<String> qualifiers = new ArrayList<>(tableId.names);
                EntityQualifier qualifier = new EntityQualifier(qualifiers, List.of(defaultDatabase, defaultSchema), dialect);
                System.out.println("Entity Qualifier[elseIF]: "+qualifier);
                joinAnalyzer.registerTableAlias(aliasId.getSimple(), qualifier);
            }
        }
    }

    private void addSourceTable(SqlNode node, SqlNodeVisitor.Result result) {
        if (node instanceof SqlIdentifier id) {
            // Skip CTEs from being added as source tables
            String tableName = getTableName(id);
            if (tableName == null || !result.getContext().getCteAliases().contains(tableName)) {
                result.getSourceTables().add(id);
            }
        } else if (node instanceof SqlBasicCall call &&
                call.getOperator().getName().equalsIgnoreCase("AS") &&
                call.getOperandList().get(0) instanceof SqlIdentifier tableId) {
            // Skip CTEs from being added as source tables
            String tableName = getTableName(tableId);
            if (tableName == null || !result.getContext().getCteAliases().contains(tableName)) {
                result.getSourceTables().add(tableId);
            }
        } else if (node instanceof SqlJoin joinNode) {
            addSourceTable(joinNode.getLeft(), result);
            addSourceTable(joinNode.getRight(), result);
        }
    }

    private String getTableName(SqlIdentifier id) {
        if (id instanceof com.calcite_new.sql.SqlTableIdentifier tableId) {
            return tableId.getTableName();
        } else if (id instanceof com.calcite_new.sql.SqlViewIdentifier viewId) {
            return viewId.getViewName();
        } else if (id.names != null && !id.names.isEmpty()) {
            return id.names.get(id.names.size() - 1);
        }
        return null;
    }

    private boolean checkCaseExpressionForInsensitiveComparison(SqlCase caseCall) {
        for (SqlNode operand : caseCall.getWhenOperands()) {
            if (operand instanceof SqlBasicCall) {
                if (SqlConditionUtils.hasCaseInsensitiveComparison(operand)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<String> extractJoinTableNames(SqlJoin join) {
        List<String> tableNames = new ArrayList<>();
        addTableName(join.getLeft(), tableNames);
        addTableName(join.getRight(), tableNames);
        return tableNames;
    }

    private void addTableName(SqlNode node, List<String> tableNames) {
        if (node instanceof SqlIdentifier id) {
            String fullQualifiedName = String.join(".", id.names);
            if (!tableNames.contains(fullQualifiedName)) {
                tableNames.add(fullQualifiedName);
            }
        } else if (node instanceof SqlBasicCall call &&
                call.getOperator().getName().equalsIgnoreCase("AS")) {
            if (call.getOperandList().get(0) instanceof SqlIdentifier tableId) {
                String fullQualifiedName = String.join(".", tableId.names);
                if (!tableNames.contains(fullQualifiedName)) {
                    tableNames.add(fullQualifiedName);
                }
            }
        } else if (node instanceof SqlJoin joinNode) {
            addTableName(joinNode.getLeft(), tableNames);
            addTableName(joinNode.getRight(), tableNames);
        }
    }
}
