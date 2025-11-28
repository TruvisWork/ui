package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.sql.SqlMerge;
import com.calcite_new.sql.SqlUpdate;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCase;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.util.SqlShuttle;
import com.calcite_new.sql.SqlCreateTable;
import com.calcite_new.sql.SqlCreateView;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.SqlColumnIdentifier;
import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.sql.core.processor.DefaultQualifiers;
import com.calcite_new.core.resolver.EntityResolver;
import org.checkerframework.checker.nullness.qual.Nullable;
import com.calcite_new.sql.core.processor.visitor.qualifier.TableQualifier;
import com.calcite_new.sql.core.processor.visitor.qualifier.ColumnQualifier;
import com.calcite_new.sql.core.processor.visitor.expander.WildcardExpander;
import com.calcite_new.sql.core.processor.visitor.scope.ScopeManager;
import com.calcite_new.sql.core.processor.visitor.scope.SubqueryInfo;
import com.calcite_new.sql.core.processor.visitor.scope.ComputedColumnInfo;

import java.util.*;

/**
 * A focused SqlShuttle that enriches SQL nodes with entity information and qualifies identifiers.
 * This class coordinates the enrichment process using specialized components following the Single Responsibility Principle.
 * <p>
 * The enrichment process includes:
 * - Qualifying unqualified table and column identifiers
 * - Attaching entity metadata to identifiers
 * - Managing query scopes for nested queries
 * - Handling table aliases and subqueries
 */
@Slf4j
public class SqlNodeEnricher extends SqlShuttle {

    private final TableQualifier tableQualifier;
    private final ColumnQualifier columnQualifier;
    private final WildcardExpander wildcardExpander;
    private final ScopeManager scopeManager;
    private final SqlDialect dialect;

    public SqlNodeEnricher(DefaultQualifiers defaultQualifiers,
                           SqlDialect dialect,
                           EntityResolver entityResolver) {
        Objects.requireNonNull(defaultQualifiers, "defaultQualifiers cannot be null");
        Objects.requireNonNull(dialect, "dialect cannot be null");
        Objects.requireNonNull(entityResolver, "entityResolver cannot be null");

        this.dialect = dialect;

        // Initialize scope manager first as it's needed by other components
        this.scopeManager = new ScopeManager();

        // Initialize qualifiers and expander
        this.tableQualifier = new TableQualifier(defaultQualifiers, dialect, entityResolver, scopeManager);
        this.columnQualifier = new ColumnQualifier(defaultQualifiers, dialect, entityResolver, scopeManager);
        this.wildcardExpander = new WildcardExpander(scopeManager);
    }

    /**
     * Enriches SqlIdentifier nodes by qualifying them and attaching entity information.
     */
    @Override
    public SqlNode visit(SqlIdentifier id) {
        if (id instanceof SqlTableIdentifier tableId) {
            return tableQualifier.qualifyTableIdentifier(tableId);
        } else if (id instanceof SqlComputedColumnIdentifier computedColumnId) {
            return computedColumnId;
        } else if (id instanceof SqlColumnIdentifier columnId) {
            return columnQualifier.qualifyColumnIdentifier(columnId);
        }
        return super.visit(id);
    }

    /**
     * Override visit(SqlCall) to handle specific SqlCall types explicitly
     * This prevents custom SQL classes from being converted to SqlBasicCall
     */
    @Override
    public SqlNode visit(SqlCall call) {
        if (call == null) {
            return null;
        }

        try {
            // Handle your custom SQL statement types
            if (call instanceof SqlCreateTable) {
                return visitCreateTable((SqlCreateTable) call);
            } else if (call instanceof SqlCreateView) {
                return visitCreateView((SqlCreateView) call);
            } else if (call instanceof SqlMerge) {
                return visitCustomMerge((SqlMerge) call);
            } else if (call instanceof SqlUpdate) {
                return visitCustomUpdate((SqlUpdate) call);
            }
            // Handle standard Calcite SQL statement types
            else if (call instanceof SqlSelect) {
                return visitSelect((SqlSelect) call);
            } else if (call instanceof SqlInsert) {
                return visitInsert((SqlInsert) call);
            } else if (call instanceof SqlDelete) {
                return visitDelete((SqlDelete) call);
            } else if (call instanceof SqlWith) {
                return visitWith((SqlWith) call);
            } else if (call instanceof SqlOrderBy) {
                return visitOrderByNode((SqlOrderBy) call);
            } else if (call instanceof SqlJoin) {
                return visitJoin((SqlJoin) call);
            }

            // Handle AS operations to track table aliases
            if (call instanceof SqlBasicCall basicCall &&
                    basicCall.getOperator() != null &&
                    basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                    basicCall.getOperandList().size() >= 2) {
                return handleAsOperation(basicCall);
            }

            // Handle other SqlBasicCall operations by visiting their operands
            if (call instanceof SqlBasicCall basicCall) {
                return visitBasicCall(basicCall);
            }

            // Handle SqlCase
            if (call instanceof SqlCase) {
                return super.visit(call);
            }

            // For other SqlCall types, use the default implementation
            return super.visit(call);
        } catch (Exception e) {
            log.warn("Failed to visit SqlCall: {}", call, e);
            return call; // Return original on failure
        }
    }

    /**
     * Handles AS operations for table aliasing and scope management.
     */
    private SqlNode handleAsOperation(SqlBasicCall asCall) {
        if (asCall.getOperandList().size() < 2) {
            return super.visit(asCall);
        }

        SqlNode left = asCall.getOperandList().get(0).accept(this);
        SqlNode right = asCall.getOperandList().get(1);

        if (right instanceof SqlIdentifier aliasId) {
            String alias;
            if (aliasId.names.size() == 1) {
                alias = aliasId.getSimple();
            } else {
                alias = aliasId.names.get(aliasId.names.size() - 1);
            }

            if (left instanceof SqlTableIdentifier tableId) {
                // Check if this is a CTE reference
                String tableName = tableId.getTableName();

                if (tableName != null && scopeManager.hasSubquery(tableName)) {
                    // CTE alias - register alias to point to CTE's projections
                    SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                    if (cteInfo != null) {
                        scopeManager.addSubqueryInfo(alias, cteInfo);
                    } else {
                        log.warn("CTE '{}' found in scope but SubqueryInfo is null", tableName);
                    }
                } else if (tableId.getEntity() != null) {
                    // Regular table alias - add to scope for future reference
                    scopeManager.addTableAlias(alias, tableId);
                }
            } else if (left instanceof SqlViewIdentifier viewId) {
                // View alias - add to scope for future reference only if it has an entity
                if (viewId.getEntity() != null) {
                    scopeManager.addTableAlias(alias, viewId);
                }
            } else if (left instanceof SqlSelect subquery) {
                // Subquery alias - create virtual table and add column projections
                List<SubqueryInfo.ColumnProjection> projectedColumns = extractProjectedColumns(subquery);
                SubqueryInfo subqueryInfo = new SubqueryInfo(subquery, projectedColumns);
                scopeManager.addSubqueryInfo(alias, subqueryInfo);
            }
        }

        return super.visit(asCall);
    }

    // Visit methods for custom SQL statement types

    private SqlCreateTable visitCreateTable(SqlCreateTable createTable) {
        SqlIdentifier name = (SqlIdentifier) accept(createTable.getName());
        SqlNodeList columnList = visitNodeList(createTable.getColumnList());
        SqlNode query = accept(createTable.getQuery());

        return new SqlCreateTable(
                createTable.getParserPosition(),
                createTable.getReplace(),
                createTable.ifNotExists,
                name,
                columnList,
                query
        );
    }

    private SqlCreateView visitCreateView(SqlCreateView createView) {
        SqlIdentifier name = (SqlIdentifier) accept(createView.getName());
        SqlNodeList columnList = visitNodeList(createView.getColumnList());

        // Process the view query in its own scope to properly handle aliases
        try (ScopeContext scope = createScopeContext()) {
            // First pass: collect all table references and their aliases
            if (createView.getQuery() instanceof SqlSelect select) {
                collectTableAliasesFromSelect(select);
            }

            SqlNode query = accept(createView.getQuery());
            return new SqlCreateView(
                    createView.getParserPosition(),
                    createView.getReplace(),
                    createView.ifNotExists,
                    name,
                    columnList,
                    query
            );
        }
    }

    private SqlMerge visitCustomMerge(SqlMerge merge) {
        try (ScopeContext scope = createScopeContext()) {
            // First pass: collect table aliases from target and source tables
            collectTableAliasesFromNode(merge.getTargetTable());
            collectTableAliasesFromNode(merge.getSource());

            // Process target and source tables with aliases available
            SqlNode targetTable = accept(merge.getTargetTable());
            SqlNode source = accept(merge.getSource());

            // Process condition with aliases available
            SqlNode condition = accept(merge.getCondition());

            // Process insert call with aliases available
            SqlInsert insertCall = (SqlInsert) accept(merge.getInsertCall());
            SqlIdentifier alias = (SqlIdentifier) accept(merge.getAlias());
            SqlNode insertCondition = accept(merge.getInsertCondition());

            // Visit matched clauses with aliases available
            List<SqlMerge.MatchedClause> newMatchedClauses = new ArrayList<>();
            for (SqlMerge.MatchedClause clause : merge.getMatchedClauses()) {
                SqlNode newCondition = accept(clause.condition);
                SqlNode newAction = accept(clause.action);
                newMatchedClauses.add(new SqlMerge.MatchedClause(newCondition, newAction));
            }

            return new SqlMerge(
                    merge.getParserPosition(),
                    targetTable,
                    condition,
                    source,
                    newMatchedClauses,
                    insertCall,
                    alias,
                    insertCondition
            );
        }
    }

    private SqlUpdate visitCustomUpdate(SqlUpdate update) {
        try (ScopeContext scope = createScopeContext()) {
            // First pass: collect table aliases from FROM clause if present
            if (update.getSource() != null) {
                collectTableAliasesFromNode(update.getSource());
            }

            // Process target table
            SqlNode targetTable = accept(update.getTargetTable());

            // Process the rest with aliases available
            SqlNodeList targetColumnList = buildUpdateTargetColumnList(update.getTargetColumnList(), targetTable);
            SqlNodeList sourceExpressionList = visitNodeList(update.getSourceExpressionList());
            SqlNode condition = accept(update.getCondition());
            SqlNode source = accept(update.getSource());
            SqlIdentifier alias = (SqlIdentifier) accept(update.getAlias());

            return new SqlUpdate(
                    update.getParserPosition(),
                    targetTable,
                    targetColumnList,
                    sourceExpressionList,
                    condition,
                    source,
                    alias
            );
        }
    }

    private SqlNodeList buildUpdateTargetColumnList(SqlNodeList originalTargetColumns, SqlNode targetTable) {
        if (originalTargetColumns == null) {
            return null;
        }

        DatabaseEntity tableEntity = null;
        if (targetTable instanceof SqlTableIdentifier tableId) {
            tableEntity = tableId.getEntity();
        } else if (targetTable instanceof SqlViewIdentifier viewId) {
            tableEntity = viewId.getEntity();
        } else if (targetTable instanceof SqlBasicCall basicCall &&
                basicCall.getOperator() != null &&
                basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                basicCall.getOperandList().size() >= 1) {
            SqlNode tableNode = basicCall.getOperandList().get(0);
            if (tableNode instanceof SqlTableIdentifier tableId) {
                tableEntity = tableId.getEntity();
            } else if (tableNode instanceof SqlViewIdentifier viewId) {
                tableEntity = viewId.getEntity();
            }
        }

        List<SqlNode> processedColumns = new ArrayList<>();
        for (SqlNode columnNode : originalTargetColumns.getList()) {
            if (columnNode instanceof SqlColumnIdentifier columnId) {
                if (tableEntity != null && columnId.getEntity() == null) {
                    Column columnEntity = createColumnEntity(tableEntity, columnId.getColumnName());
                    columnId.setEntity(columnEntity);
                    columnId.setDatabaseEntity(tableEntity);
                }
                processedColumns.add(columnId);
            } else {
                processedColumns.add(columnNode);
            }
        }

        return new SqlNodeList(processedColumns, originalTargetColumns.getParserPosition());
    }

    // Visit methods for standard Calcite SQL statement types

    private SqlSelect visitSelect(SqlSelect select) {
        try (ScopeContext scope = createScopeContext()) {
            // Process FROM clause first to establish table context
            // Use visitFrom() to ensure proper handling of table references and subqueries
            SqlNode from = visitFrom(select.getFrom());

            // Process SELECT list
            SqlNodeList selectList = visitSelectList(select.getSelectList());
            extractAndRegisterComputedColumns(selectList);

            scopeManager.setSelectListItems(selectList.getList());

            // Process all other clauses
            SqlNode where = visitWhere(select.getWhere());
            SqlNodeList groupBy = visitGroupBy(select.getGroup(), selectList);
            SqlNode having = visitHaving(select.getHaving());
            SqlNodeList windowDecls = visitWindowDecls(select.getWindowList());
            SqlNode qualify = visitQualify(select.getQualify());
            SqlNodeList orderBy = visitOrderBy(select.getOrderList(), selectList);
            SqlNode offset = visitOffset(select.getOffset());
            SqlNode fetch = visitFetch(select.getFetch());

            return new SqlSelect(
                    select.getParserPosition(),
                    select.isDistinct() ? (SqlNodeList) select.getModifierNode(SqlSelectKeyword.DISTINCT)
                            : new SqlNodeList(SqlParserPos.ZERO),
                    selectList,
                    from,
                    where,
                    groupBy,
                    having,
                    windowDecls,
                    qualify,
                    orderBy,
                    offset,
                    fetch,
                    select.getHints()
            );
        } catch (Exception e) {
            log.warn("Failed to process SqlSelect", e);
            return select; // Return original on failure
        }
    }

    private SqlDelete visitDelete(SqlDelete delete) {
        return scopeManager.executeInScope(() -> {
            // Process and qualify the target table first
            SqlNode targetTable = accept(delete.getTargetTable());

            // Add the target table to scope for column resolution in WHERE clause
            if (targetTable instanceof SqlTableIdentifier tableId) {
                scopeManager.addTable(tableId);
                // Also add as alias using table name for unqualified column references
                String tableName = tableId.getTableName();
                if (tableName != null) {
                    scopeManager.addTableAlias(tableName, tableId);
                }
            } else if (targetTable instanceof SqlViewIdentifier viewId) {
                scopeManager.addTable(viewId);
                // Also add as alias using view name for unqualified column references
                String viewName = viewId.getViewName();
                if (viewName != null) {
                    scopeManager.addTableAlias(viewName, viewId);
                }
            }

            // Now process the condition with table context available
            SqlNode condition = accept(delete.getCondition());

            // Process other components
            SqlSelect sourceSelect = (SqlSelect) accept(delete.getSourceSelect());
            SqlIdentifier alias = (SqlIdentifier) accept(delete.getAlias());

            return new SqlDelete(
                    delete.getParserPosition(),
                    targetTable,
                    condition,
                    sourceSelect,
                    alias
            );
        });
    }

    private SqlInsert visitInsert(SqlInsert insert) {
        return scopeManager.executeInScope(() -> {
            // Process target table first to establish context
            SqlNode targetTable = accept(insert.getTargetTable());

            // Don't qualify the target column list - these are simple column names for the target table
            SqlNodeList originalTargetColumnList = insert.getTargetColumnList();
            final SqlNodeList targetColumnList;
            if (originalTargetColumnList != null) {
                // Keep the original column names as simple identifiers
                List<SqlNode> originalColumns = new ArrayList<>();
                for (SqlNode column : originalTargetColumnList.getList()) {
                    if (column instanceof SqlColumnIdentifier && targetTable instanceof SqlIdentifier) {
                        DatabaseEntity tableEntity = null;
                        if (targetTable instanceof SqlTableIdentifier) {
                            tableEntity = ((SqlTableIdentifier) targetTable).getEntity();
                        } else if (targetTable instanceof SqlViewIdentifier) {
                            tableEntity = ((SqlViewIdentifier) targetTable).getEntity();
                        }
                        if (tableEntity != null) {
                            Column columnEntity = createColumnEntity(tableEntity, ((SqlColumnIdentifier) column).getColumnName());
                            ((SqlColumnIdentifier) column).setEntity(columnEntity);
                        }
                        originalColumns.add(column);
                    } else if (column instanceof SqlColumnIdentifier && targetTable instanceof SqlBasicCall) {
                        SqlNode table = ((SqlBasicCall) targetTable).getOperandList().get(0);
                        DatabaseEntity tableEntity = null;
                        if (table instanceof SqlTableIdentifier) {
                            tableEntity = ((SqlTableIdentifier) table).getEntity();
                        } else if (table instanceof SqlViewIdentifier) {
                            tableEntity = ((SqlViewIdentifier) table).getEntity();
                        }
                        if (tableEntity != null) {
                            Column columnEntity = createColumnEntity(tableEntity, ((SqlColumnIdentifier) column).getColumnName());
                            ((SqlColumnIdentifier) column).setEntity(columnEntity);
                        }
                        originalColumns.add(column);
                    }
                }
                targetColumnList = new SqlNodeList(originalColumns, originalTargetColumnList.getParserPosition());
            } else {
                targetColumnList = null;
            }

            // Process source (SELECT) in the same scope so it can access table context
            SqlNode source = accept(insert.getSource());
            return new SqlInsert(
                    insert.getParserPosition(),
                    new SqlNodeList(SqlParserPos.ZERO),
                    targetTable,
                    source,
                    targetColumnList
            );
        });
    }

    private SqlWith visitWith(SqlWith with) {
        // DON'T create a new scope - CTEs should be available in the main body's scope
        // Process each CTE definition and register it in the CURRENT scope
        List<SqlNode> processedWithItems = new ArrayList<>();

        if (with.withList != null) {
            for (SqlNode withItem : with.withList) {
                if (withItem instanceof SqlWithItem cteItem) {
                    // Extract CTE name
                    String cteName = cteItem.name != null ? cteItem.name.getSimple() : null;

                    // Process the CTE query to enrich it
                    SqlNode processedQuery = accept(cteItem.query);

                    // Extract the underlying SELECT query for column projection extraction
                    SqlSelect processedSelectQuery = extractSelectFromNode(processedQuery);

                    // Also get the original (unprocessed) SELECT to check structure
                    SqlSelect originalSelectQuery = extractSelectFromNode(cteItem.query);

                    // If we have a SELECT, extract projected columns and register the CTE
                    if (processedSelectQuery != null && cteName != null) {
                        List<SubqueryInfo.ColumnProjection> projectedColumns = null;

                        // Special handling for CTEs that do SELECT * FROM another CTE
                        // Check the ORIGINAL (unprocessed) query structure
                        if (originalSelectQuery != null && originalSelectQuery.getFrom() != null && originalSelectQuery.getSelectList() != null) {
                            // Check if select list is just a wildcard
                            boolean isSimpleSelectStar = false;
                            for (SqlNode item : originalSelectQuery.getSelectList()) {
                                if (item instanceof SqlIdentifier id && id.isStar()) {
                                    isSimpleSelectStar = true;
                                    break;
                                }
                            }

                            // If it's SELECT * FROM CTE, try to get projections from the CTE directly
                            if (isSimpleSelectStar && originalSelectQuery.getFrom() instanceof SqlIdentifier fromId) {
                                String fromTableName = fromId.getSimple();
                                if (fromTableName != null && scopeManager.hasSubquery(fromTableName)) {
                                    SubqueryInfo sourceCteInfo = scopeManager.getSubqueryInfo(fromTableName);
                                    if (sourceCteInfo != null) {
                                        projectedColumns = sourceCteInfo.getColumnProjections();
                                    }
                                }
                            }
                        }

                        // Fallback to normal extraction if we didn't use direct projections
                        if (projectedColumns == null) {
                            projectedColumns = extractProjectedColumns(processedSelectQuery);
                        }

                        SubqueryInfo cteInfo = new SubqueryInfo(processedSelectQuery, projectedColumns);
                        scopeManager.addSubqueryInfo(cteName, cteInfo);
                    }

                    // Create new SqlWithItem with processed query
                    @SuppressWarnings("deprecation")
                    SqlWithItem processedItem = new SqlWithItem(
                            cteItem.getParserPosition(),
                            cteItem.name,
                            cteItem.columnList,
                            processedQuery
                    );
                    processedWithItems.add(processedItem);
                } else {
                    processedWithItems.add(accept(withItem));
                }
            }
        }

        // Process the main query body with CTEs now available in scope
        SqlNode processedBody = accept(with.body);

        return new SqlWith(
                with.getParserPosition(),
                new SqlNodeList(processedWithItems, with.withList.getParserPosition()),
                processedBody
        );
    }


    /**
     * Handles SqlBasicCall operations by visiting their operands.
     */
    private SqlNode visitBasicCall(SqlBasicCall basicCall) {
        try {
            // Handle set operations (UNION, UNION ALL, INTERSECT, EXCEPT) specially
            if (basicCall.getOperator() != null) {
                String operatorName = basicCall.getOperator().getName();
                if (operatorName.equalsIgnoreCase("UNION") ||
                        operatorName.equalsIgnoreCase("UNION ALL") ||
                        operatorName.equalsIgnoreCase("INTERSECT") ||
                        operatorName.equalsIgnoreCase("EXCEPT")) {

                    // For set operations, process each operand as a complete query
                    List<SqlNode> newOperands = new ArrayList<>();
                    for (int i = 0; i < basicCall.getOperandList().size(); i++) {
                        SqlNode operand = basicCall.getOperandList().get(i);
                        try {
                            // For set operations, each operand should be a SELECT statement
                            // Use visitSelect() if it's a SqlSelect, otherwise use accept()
                            SqlNode processedOperand;
                            if (operand instanceof SqlSelect) {
                                processedOperand = visitSelect((SqlSelect) operand);
                            } else {
                                processedOperand = operand.accept(this);
                            }
                            newOperands.add(processedOperand);
                        } catch (Exception e) {
                            log.warn("Failed to process set operation operand {} in SqlBasicCall: {}", i, operand.getClass().getSimpleName(), e);
                            // Add the original operand if processing fails
                            newOperands.add(operand);
                        }
                    }

                    SqlNode result = new SqlBasicCall(basicCall.getOperator(), newOperands, basicCall.getParserPosition());
                    return result;
                }
            }

            // Handle AS operations specially to ensure nested subqueries are processed
            if (basicCall.getOperator() != null &&
                    basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                    basicCall.getOperandList().size() == 2) {

                SqlNode firstOperand = basicCall.getOperandList().get(0);
                SqlNode secondOperand = basicCall.getOperandList().get(1);

                // Process the first operand thoroughly - this might be a nested subquery or table identifier
                SqlNode processedFirstOperand;
                if (firstOperand instanceof SqlSelect) {
                    processedFirstOperand = visitSelect((SqlSelect) firstOperand);
                } else if (firstOperand instanceof SqlIdentifier) {
                    processedFirstOperand = visit((SqlIdentifier) firstOperand);
                } else {
                    processedFirstOperand = firstOperand.accept(this);
                }

                // Process the second operand (usually an alias)
                SqlNode processedSecondOperand;
                if (secondOperand instanceof SqlIdentifier) {
                    processedSecondOperand = visit((SqlIdentifier) secondOperand);
                } else {
                    processedSecondOperand = secondOperand.accept(this);
                }

                // Create new AS call with processed operands
                return new SqlBasicCall(
                        basicCall.getOperator(),
                        List.of(processedFirstOperand, processedSecondOperand),
                        basicCall.getParserPosition()
                );
            }

            // Handle other SqlBasicCall operations (AS, etc.)
            List<SqlNode> newOperands = new ArrayList<>();
            for (int i = 0; i < basicCall.getOperandList().size(); i++) {
                SqlNode operand = basicCall.getOperandList().get(i);
                try {
                    // Explicitly handle SqlIdentifier instances (SqlShuttle doesn't automatically dispatch to visit(SqlIdentifier))
                    SqlNode processedOperand;
                    if (operand instanceof SqlIdentifier) {
                        processedOperand = visit((SqlIdentifier) operand);
                    } else {
                        processedOperand = operand.accept(this);
                    }
                    newOperands.add(processedOperand);
                } catch (Exception e) {
                    log.warn("Failed to process operand {} in SqlBasicCall: {}", i, operand.getClass().getSimpleName(), e);
                    // Add the original operand if processing fails
                    newOperands.add(operand);
                }
            }

            // Create new SqlBasicCall with visited operands
            SqlNode result = new SqlBasicCall(basicCall.getOperator(), newOperands, basicCall.getParserPosition());
            return result;
        } catch (Exception e) {
            log.warn("Failed to process SqlBasicCall with operator: {}",
                    basicCall.getOperator() != null ? basicCall.getOperator().getName() : "null", e);
            return basicCall; // Return original on failure
        }
    }

    // Visit helper methods

    private SqlNode visitFrom(SqlNode from) {
        if (from == null) {
            return null;
        }

        try {
            if (from instanceof SqlJoin join) {

                // Process the join operands to register any subquery aliases
                SqlNode left = visitFrom(join.getLeft());
                SqlNode right = visitFrom(join.getRight());

                // Create a new join with processed operands
                SqlNode condition = join.getCondition() != null ? join.getCondition().accept(this) : null;
                return new SqlJoin(
                        join.getParserPosition(),
                        left,
                        join.isNaturalNode(),
                        join.getJoinTypeNode(),
                        right,
                        join.getConditionTypeNode(),
                        condition
                );
            } else if (from instanceof SqlIdentifier tableId) {
                // Handle table/view references with potential aliases

                // Convert plain SqlIdentifier to SqlTableIdentifier if it looks like a table reference
                SqlNode processedTable;
                if (tableId instanceof SqlTableIdentifier) {
                    processedTable = tableQualifier.qualifyTableIdentifier((SqlTableIdentifier) tableId);
                } else {
                    // This is a plain SqlIdentifier in a FROM clause - treat it as a table reference
                    SqlTableIdentifier tableIdentifier = new SqlTableIdentifier(
                            tableId.names,
                            tableId.getParserPosition(),
                            null // Entity will be resolved during qualification
                    );
                    processedTable = tableQualifier.qualifyTableIdentifier(tableIdentifier);
                }

                if (processedTable instanceof SqlTableIdentifier processedTableId) {
                    // Only add to scope if it has an entity (not a CTE reference)
                    if (processedTableId.getEntity() != null) {
                        scopeManager.addTable(processedTableId);
                    }
                } else if (processedTable instanceof SqlViewIdentifier processedViewId) {
                    // Only add to scope if it has an entity (not a CTE reference)
                    if (processedViewId.getEntity() != null) {
                        scopeManager.addTable(processedViewId);
                    }
                }
                return processedTable;
            } else if (from instanceof SqlBasicCall basicCall) {
                // Process all operands of SqlBasicCall to ensure table references are qualified
                SqlNode processedCall = visitBasicCall(basicCall);

                // Handle table with alias (e.g., table AS alias or table alias) after processing
                if (processedCall instanceof SqlBasicCall processedBasicCall &&
                        processedBasicCall.getOperator() != null &&
                        processedBasicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                        processedBasicCall.getOperandList().size() == 2) {
                    try {
                        SqlNode left = processedBasicCall.getOperandList().get(0);
                        SqlNode right = processedBasicCall.getOperandList().get(1);


                        if (right instanceof SqlIdentifier) {
                            String alias = ((SqlIdentifier) right).getSimple();
                            trackTableAlias(left, alias);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to process AS alias in FROM clause", e);
                    }
                }

                return processedCall;
            } else if (from instanceof SqlSelect subquery) {
                // Handle subqueries in FROM clause - ensure they are fully processed
                SqlNode processedSubquery = visitSelect(subquery);
                return processedSubquery;
            }
            return accept(from);
        } catch (Exception e) {
            log.warn("Failed to process FROM clause node type: {}", from.getClass().getSimpleName(), e);
            return from; // Return original on failure
        }
    }

    private SqlNode visitJoin(SqlJoin join) {
        try {
            SqlNode left = processJoinOperand(join.getLeft());
            trackTableFromNode(left);

            SqlNode right = processJoinOperand(join.getRight());
            trackTableFromNode(right);

            SqlNode condition = join.getCondition() != null ?
                    join.getCondition().accept(this) : null;

            return new SqlJoin(
                    join.getParserPosition(),
                    left,
                    join.isNaturalNode(),
                    join.getJoinTypeNode(),
                    right,
                    join.getConditionTypeNode(),
                    condition
            );
        } catch (Exception e) {
            log.warn("Failed to process JOIN, falling back to standard processing", e);
            trackTableFromNode(join.getLeft());
            trackTableFromNode(join.getRight());
            return join.accept(this);
        }
    }

    /**
     * Processes a join operand to ensure proper entity attachment.
     * Handles tables, subqueries, and aliases correctly.
     */
    private SqlNode processJoinOperand(SqlNode operand) {
        if (operand == null) {
            return null;
        }

        try {
            // Handle different types of join operands
            if (operand instanceof SqlTableIdentifier) {
                // This is a table reference - qualify it
                return tableQualifier.qualifyTableIdentifier((SqlTableIdentifier) operand);
            } else if (operand instanceof SqlSelect) {
                // This is a subquery - process it fully
                return visitSelect((SqlSelect) operand);
            } else if (operand instanceof SqlBasicCall basicCall &&
                    basicCall.getOperator() != null &&
                    basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                    basicCall.getOperandList().size() == 2) {
                // Handle table/subquery AS alias - process the first operand
                SqlNode firstOperand = basicCall.getOperandList().get(0);
                SqlNode processedOperand = processJoinOperand(firstOperand);

                // Return the original AS call but with processed first operand
                return new SqlBasicCall(
                        basicCall.getOperator(),
                        List.of(processedOperand, basicCall.getOperandList().get(1)),
                        basicCall.getParserPosition()
                );
            } else {
                // For other types, use standard processing
                return operand.accept(this);
            }
        } catch (Exception e) {
            log.warn("Failed to process join operand: {}, falling back to standard processing", operand.getClass().getSimpleName(), e);
            return operand.accept(this); // Fall back to standard processing
        }
    }

    private SqlNodeList visitSelectList(SqlNodeList selectList) {
        if (selectList == null) {
            return null;
        }

        List<SqlNode> newNodes = new ArrayList<>();
        boolean changed = false;

        for (SqlNode selectItem : selectList.getList()) {
            List<SqlNode> processedItems = processSelectItemWithExpansion(selectItem);
            newNodes.addAll(processedItems);
            if (processedItems.size() != 1 || processedItems.get(0) != selectItem) {
                changed = true;
            }
        }

        return changed ? new SqlNodeList(newNodes, selectList.getParserPosition()) : selectList;
    }

    private SqlNode visitWhere(SqlNode where) {
        return accept(where);
    }

    private SqlNodeList visitGroupBy(SqlNodeList groupBy, SqlNodeList selectList) {
        if (groupBy == null) {
            return null;
        }

        List<SqlNode> newNodes = new ArrayList<>();
        boolean changed = false;

        for (SqlNode groupByItem : groupBy.getList()) {
            SqlNode resolvedItem = resolveOrdinalIfNeeded(groupByItem, selectList, SqlOrdinalReference.OrdinalContext.GROUP_BY);
            newNodes.add(resolvedItem != null ? resolvedItem : accept(groupByItem));
            if (resolvedItem != groupByItem) {
                changed = true;
            }
        }

        return changed ? new SqlNodeList(newNodes, groupBy.getParserPosition()) : groupBy;
    }

    private SqlNode visitHaving(SqlNode having) {
        return accept(having);
    }

    private SqlNodeList visitWindowDecls(SqlNodeList windowDecls) {
        return visitNodeList(windowDecls);
    }

    private SqlNode visitQualify(@Nullable SqlNode qualify) {
        return accept(qualify);
    }

    private SqlNode visitOrderByNode(SqlOrderBy orderBy) {
        // Check if the underlying query is a SqlWith
        if (orderBy.query instanceof SqlWith) {
            // Process the SqlWith first
            SqlNode processedWith = visitWith((SqlWith) orderBy.query);

            // Create new SqlOrderBy with processed SqlWith
            return new SqlOrderBy(
                    orderBy.getParserPosition(),
                    processedWith,
                    orderBy.orderList,
                    orderBy.offset,
                    orderBy.fetch
            );
        } else {
            // Process normally
            SqlNode processedQuery = accept(orderBy.query);
            SqlNodeList processedOrderList = orderBy.orderList != null ?
                    (SqlNodeList) accept(orderBy.orderList) : null;
            SqlNode processedOffset = orderBy.offset != null ?
                    accept(orderBy.offset) : null;
            SqlNode processedFetch = orderBy.fetch != null ?
                    accept(orderBy.fetch) : null;

            return new SqlOrderBy(
                    orderBy.getParserPosition(),
                    processedQuery,
                    processedOrderList,
                    processedOffset,
                    processedFetch
            );
        }
    }

    private SqlNodeList visitOrderBy(SqlNodeList orderBy, SqlNodeList selectList) {
        if (orderBy == null) {
            return null;
        }

        List<SqlNode> newNodes = new ArrayList<>();
        boolean changed = false;

        for (SqlNode orderByItem : orderBy.getList()) {
            SqlNode resolvedItem = resolveOrdinalIfNeeded(orderByItem, selectList, SqlOrdinalReference.OrdinalContext.ORDER_BY);
            newNodes.add(resolvedItem != null ? resolvedItem : accept(orderByItem));
            if (resolvedItem != orderByItem) {
                changed = true;
            }
        }

        return changed ? new SqlNodeList(newNodes, orderBy.getParserPosition()) : orderBy;
    }

    private SqlNode visitOffset(SqlNode offset) {
        return accept(offset);
    }

    private SqlNode visitFetch(SqlNode fetch) {
        return accept(fetch);
    }

    private SqlNodeList visitNodeList(SqlNodeList nodeList) {
        if (nodeList == null) {
            return null;
        }

        List<SqlNode> newNodes = new ArrayList<>();
        boolean changed = false;

        for (SqlNode node : nodeList.getList()) {
            SqlNode newNode = accept(node);
            newNodes.add(newNode);
            if (newNode != node) {
                changed = true;
            }
        }

        return changed ? new SqlNodeList(newNodes, nodeList.getParserPosition()) : nodeList;
    }

    private SqlNode accept(SqlNode node) {
        return node == null ? null : node.accept(this);
    }

    // Helper methods for table alias tracking

    /**
     * Tracks table/view/CTE aliases from FROM clauses
     */
    private void trackTableAlias(SqlNode tableNode, String alias) {
        if (tableNode == null || alias == null) {
            return;
        }

        // Handle subquery aliases
        if (tableNode instanceof SqlSelect subquery) {
            // Extract column projections from the subquery
            // IMPORTANT: Call extractProjectedColumns with the original subquery to ensure we get the enriched columns
            List<SubqueryInfo.ColumnProjection> projectedColumns = extractProjectedColumns(subquery);
            // However, we need to work with the enriched subquery structure
            // If the subquery has already been enriched (wildcards expanded), we need to account for that
            SubqueryInfo subqueryInfo = new SubqueryInfo(subquery, projectedColumns);
            scopeManager.addSubqueryInfo(alias, subqueryInfo);
            return;
        }

        // Check if tableNode is a CTE reference
        if (tableNode instanceof SqlTableIdentifier tableId) {
            String tableName = tableId.getTableName();
            if (tableName != null && scopeManager.hasSubquery(tableName)) {
                // This is a CTE reference - register the alias to point to the CTE's column projections
                SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                if (cteInfo != null) {
                    scopeManager.addSubqueryInfo(alias, cteInfo);
                }
                return;
            }
            scopeManager.addTableAlias(alias, tableId);
        } else if (tableNode instanceof SqlViewIdentifier viewId) {
            scopeManager.addTableAlias(alias, viewId);
        } else if (tableNode instanceof SqlIdentifier tableIdentifier) {
            // Check if it's a CTE reference
            String tableName = tableIdentifier.names.get(tableIdentifier.names.size() - 1);
            if (scopeManager.hasSubquery(tableName)) {
                SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                if (cteInfo != null) {
                    scopeManager.addSubqueryInfo(alias, cteInfo);
                }
                return;
            }

            // Convert to SqlTableIdentifier for tracking (default to table)
            SqlTableIdentifier trackedTable = new SqlTableIdentifier(
                    tableIdentifier.names,
                    tableIdentifier.getParserPosition(),
                    null // Entity will be resolved later if needed
            );
            scopeManager.addTableAlias(alias, trackedTable);
        }
    }

    /**
     * Tracks tables from join operands - handles real tables, views, and CTEs
     */
    private void trackTableFromNode(SqlNode node) {
        try {
            if (node instanceof SqlTableIdentifier tableId) {
                // Check if this is a CTE reference
                String tableName = tableId.getTableName();
                if (tableName != null && scopeManager.hasSubquery(tableName)) {
                    // Don't add it as a table - it's a CTE
                    return;
                }
                scopeManager.addTable(tableId);
            } else if (node instanceof SqlViewIdentifier viewId) {
                scopeManager.addTable(viewId);
            } else if (node instanceof SqlBasicCall basicCall &&
                    basicCall.getOperator() != null &&
                    basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                    basicCall.getOperandList().size() == 2) {
                // Handle table/view/CTE AS alias pattern
                SqlNode tableNode = basicCall.getOperandList().get(0);
                SqlNode aliasNode = basicCall.getOperandList().get(1);

                if (aliasNode instanceof SqlIdentifier aliasId) {
                    String alias = aliasId.getSimple();

                    // Check if the table is a CTE
                    if (tableNode instanceof SqlTableIdentifier tableId) {
                        String tableName = tableId.getTableName();
                        if (tableName != null && scopeManager.hasSubquery(tableName)) {
                            // CTE alias - register alias to point to CTE's projections
                            SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                            if (cteInfo != null) {
                                scopeManager.addSubqueryInfo(alias, cteInfo);
                            }
                            return;
                        }
                    }

                    // Regular table/view alias only - don't track subqueries here
                    if (tableNode instanceof SqlTableIdentifier || tableNode instanceof SqlViewIdentifier) {
                        scopeManager.addTableAlias(alias, (SqlIdentifier) tableNode);
                    }
                    // Subqueries are tracked separately by visitSelect and handleAsOperation
                }
            }
        } catch (Exception e) {
            // Silently handle tracking failures
        }
    }

    /**
     * Processes a single SELECT item with wildcard expansion, returning a list of nodes
     */
    private List<SqlNode> processSelectItemWithExpansion(SqlNode selectItem) {
        if (selectItem == null) {
            return new ArrayList<>();
        }

        // Handle wildcard expansion
        if (selectItem instanceof SqlIdentifier identifier && identifier.isStar()) {
            return wildcardExpander.expandWildcardToList(identifier);
        }

        // Handle EXCEPT operation
        if (selectItem instanceof SqlBasicCall basicCall &&
                basicCall.getOperator() != null &&
                basicCall.getOperator().getName().equalsIgnoreCase("EXCEPT") &&
                basicCall.getOperandList().size() == 2 &&
                basicCall.getOperandList().get(1) instanceof SqlNodeList exceptList) {

            SqlNode wildcardNode = basicCall.getOperandList().get(0);
            // Handle both unqualified (*) and qualified (table.*) wildcards
            if (wildcardNode instanceof SqlIdentifier wildcard) {
                boolean isQualifiedWildcard = wildcard.names.size() == 2 && wildcard.names.get(1).equals("*");
                if (wildcard.isStar() || isQualifiedWildcard) {
                    List<SqlNode> expanded = wildcardExpander.expandWildcardWithExceptToList(wildcard, exceptList);
                    log.debug("Expanded EXCEPT: wildcard={}, excluded={}, result size={}",
                            wildcard, exceptList, expanded.size());
                    return expanded;
                }
            }
        }

        // Handle AS operations with wildcards
        if (selectItem instanceof SqlBasicCall basicCall &&
                basicCall.getOperator() != null &&
                basicCall.getOperator().getName().equalsIgnoreCase("AS")) {

            // Handle regular AS pattern
            if (basicCall.getOperandList().size() == 2) {
                SqlNode leftOperand = basicCall.getOperandList().get(0);
                SqlNode rightOperand = basicCall.getOperandList().get(1);
                if (leftOperand instanceof SqlIdentifier leftId && leftId.isStar()) {
                    // Handle table.* AS alias pattern
                    return wildcardExpander.expandWildcardWithAliasToList(leftId, rightOperand);
                }
            }
        }

        // Process other select items normally
        SqlNode processedItem = accept(selectItem);
        return List.of(processedItem);
    }

    /**
     * Extracts the SELECT query from a node that might be wrapped in ORDER BY/LIMIT.
     * This recursively unwraps ORDER BY nodes to find the underlying SELECT.
     */
    private SqlSelect extractSelectFromNode(SqlNode node) {
        if (node == null) {
            return null;
        }

        if (node instanceof SqlSelect) {
            return (SqlSelect) node;
        }

        if (node instanceof SqlOrderBy orderBy) {
            // Recursively extract from the underlying query
            return extractSelectFromNode(orderBy.query);
        }

        // For other node types (UNION, VALUES, etc.), return null
        // Only SELECT queries are supported for CTEs in this implementation
        return null;
    }

    /**
     * Extracts projected columns from a subquery's SELECT list.
     * This method extracts source information from already-processed columns.
     */
    private List<SubqueryInfo.ColumnProjection> extractProjectedColumns(SqlSelect subquery) {
        if (subquery.getSelectList() == null) {
            return List.of();
        }
        List<SubqueryInfo.ColumnProjection> projected = new ArrayList<>();

        // IMPORTANT: Track aliases from FROM clause BEFORE processing select list
        // This ensures that wildcards like a.* can resolve the alias 'a'
        trackAliasesFromFromClause(subquery);

        for (SqlNode item : subquery.getSelectList()) {
            if (item instanceof SqlIdentifier id) {
                // Check if this is a wildcard
                // Wildcards can have names=[*] or names=[] depending on how they're parsed
                boolean isWildcard = (id.names != null && id.names.size() == 1 && id.names.get(0).equals("*")) ||
                        (id.toString().equals("*")) ||
                        (id.names != null && id.names.isEmpty() && id.toString().trim().equals("*"));

                if (isWildcard) {
                    // For wildcards, expand to all columns from the FROM clause
                    List<SubqueryInfo.ColumnProjection> expandedColumns = expandWildcardFromFromClause(subquery);
                    projected.addAll(expandedColumns);
                    continue;
                }

                // Handle wildcards like table.* (qualified wildcards)
                // Check if last name is "*" and there are multiple names
                if (id.names != null && id.names.size() > 1 && id.names.get(id.names.size() - 1).equals("*")) {
                    String tableAlias = id.names.get(0);
                    // Expand based on the table alias
                    List<SubqueryInfo.ColumnProjection> expandedColumns = expandWildcardFromTable(subquery, tableAlias);
                    if (!expandedColumns.isEmpty()) {
                        projected.addAll(expandedColumns);
                        continue;
                    }
                }

                String columnName;
                Column sourceColumn = null;
                DatabaseEntity sourceEntity = null;

                // If it's a SqlColumnIdentifier, we can get the source column
                if (id instanceof SqlColumnIdentifier columnId) {
                    sourceColumn = columnId.getEntity();
                    sourceEntity = columnId.getDatabaseEntity();
                    columnName = columnId.getColumnName();
                } else {
                    // For regular SqlIdentifier, try to qualify it to get source information
                    SqlNode qualifiedId = accept(id);

                    if (qualifiedId instanceof SqlColumnIdentifier qualifiedColumnId) {
                        sourceColumn = qualifiedColumnId.getEntity();
                        sourceEntity = qualifiedColumnId.getDatabaseEntity();
                        columnName = qualifiedColumnId.getColumnName();
                    } else {
                        // Fallback to original logic
                        if (id.names.size() == 1) {
                            columnName = id.getSimple();
                        } else {
                            columnName = id.names.get(id.names.size() - 1);
                        }

                        // Try to trace CTE column references by looking up in CTE definitions
                        // This handles cases like `p.create_timestamp` where p is a CTE alias
                        if (id.names.size() > 1) {
                            String tableOrAlias = id.names.get(0);
                            String colName = id.names.get(id.names.size() - 1);

                            // If the table is a CTE, try to find the column's source
                            if (scopeManager.hasSubquery(tableOrAlias)) {
                                SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableOrAlias);
                                SubqueryInfo.ColumnProjection projection = cteInfo.getColumnProjection(colName);
                                if (projection != null) {
                                    sourceColumn = projection.getSourceColumn();
                                    sourceEntity = projection.getSourceEntity();

                                    // If still no source, try to trace through the expression
                                    if (sourceColumn == null && sourceEntity == null) {
                                        SqlNode expr = projection.getExpression();
                                        if (expr instanceof SqlColumnIdentifier nestedColumnId) {
                                            sourceColumn = nestedColumnId.getEntity();
                                            sourceEntity = nestedColumnId.getDatabaseEntity();
                                        } else if (expr != null) {
                                            // Recursively extract source from the expression (handles CASE, function calls, etc.)
                                            SourceInfo sourceInfo = extractSourceFromExpression(expr);
                                            if (sourceInfo != null) {
                                                sourceColumn = sourceInfo.sourceColumn;
                                                sourceEntity = sourceInfo.sourceEntity;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                projected.add(new SubqueryInfo.ColumnProjection(columnName, id, sourceEntity, sourceColumn));
            } else if (item instanceof SqlBasicCall call) {
                // Handle AS operations - extract alias and expression
                if (call.getOperator() != null && call.getOperator().getName().equalsIgnoreCase("AS") &&
                        call.getOperandList().size() > 1 && call.getOperandList().get(1) instanceof SqlIdentifier aliasId) {

                    String aliasName = aliasId.names.size() == 1 ? aliasId.getSimple() : aliasId.names.get(aliasId.names.size() - 1);
                    SqlNode expression = call.getOperandList().get(0);

                    Column sourceColumn = null;
                    DatabaseEntity sourceEntity = null;

                    // The expression should already be enriched from visitSelectList
                    // Try to extract source column - handle both direct columns and expressions
                    SourceInfo sourceInfo = extractSourceFromExpression(expression);
                    if (sourceInfo != null) {
                        sourceColumn = sourceInfo.sourceColumn;
                        sourceEntity = sourceInfo.sourceEntity;
                    }

                    projected.add(new SubqueryInfo.ColumnProjection(aliasName, expression, sourceEntity, sourceColumn));
                }
            }
        }
        return projected;
    }

    /**
     * Expands a wildcard SELECT to all columns from the FROM clause.
     * This handles cases where SELECT * is used in subqueries.
     */
    private List<SubqueryInfo.ColumnProjection> expandWildcardFromFromClause(SqlSelect subquery) {
        SqlNode from = subquery.getFrom();
        if (from == null) {
            return List.of();
        }

        List<SubqueryInfo.ColumnProjection> expanded = new ArrayList<>();

        // If the FROM is a direct table reference
        if (from instanceof SqlTableIdentifier tableId) {
            String tableName = tableId.getTableName();

            // First check if this is a CTE reference
            if (tableName != null && scopeManager.hasSubquery(tableName)) {
                SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                if (cteInfo != null) {
                    List<SubqueryInfo.ColumnProjection> cteColumns = cteInfo.getColumnProjections();
                    expanded.addAll(cteColumns);
                    return expanded;
                }
            }

            // Otherwise try to get columns from the table entity
            DatabaseEntity tableEntity = tableId.getEntity();
            if (tableEntity != null) {
                // Get all columns from the table
                Collection<Column> columns = null;
                if (tableEntity instanceof Table table) {
                    columns = table.getColumns();
                } else if (tableEntity instanceof View view) {
                    columns = view.getColumns();
                }

                if (columns != null) {
                    for (Column col : columns) {
                        String colName = col.getName() != null ? col.getName().getName().toLowerCase() : null;
                        if (colName == null) {
                            continue;
                        }
                        // Create a simple SqlIdentifier for the column name
                        SqlIdentifier columnNode = new SqlIdentifier(Arrays.asList(colName), SqlParserPos.ZERO);
                        expanded.add(new SubqueryInfo.ColumnProjection(colName, columnNode, tableEntity, col));
                    }
                }
            }
        }
        // If the FROM is a plain SqlIdentifier (could be a CTE reference)
        else if (from instanceof SqlIdentifier && !(from instanceof SqlTableIdentifier)) {
            String tableName = ((SqlIdentifier) from).getSimple();

            // Check if this is a CTE reference
            if (tableName != null && scopeManager.hasSubquery(tableName)) {
                SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(tableName);
                if (cteInfo != null) {
                    List<SubqueryInfo.ColumnProjection> cteColumns = cteInfo.getColumnProjections();
                    expanded.addAll(cteColumns);
                    return expanded;
                }
            }
        }
        // If the FROM is a subquery (recursive case for nested SELECT *)
        else if (from instanceof SqlSelect subqueryFrom) {
            // Recursively extract columns from the nested subquery
            List<SubqueryInfo.ColumnProjection> nestedColumns = extractProjectedColumns(subqueryFrom);
            for (SubqueryInfo.ColumnProjection col : nestedColumns) {
                // Skip if this is a window function result (e.g., ROW_NUMBER, RANK, etc.)
                if (isWindowFunctionColumn(col.getExpression())) {
                    continue;
                }
                expanded.add(col);
            }
        }
        // If the FROM is a SqlBasicCall (e.g., AS operations or table references)
        else if (from instanceof SqlBasicCall basicCall) {
            // Try to extract the underlying table or subquery from the call
            for (int i = 0; i < basicCall.getOperandList().size(); i++) {
                SqlNode operand = basicCall.getOperandList().get(i);
                // Check if any operand is a table
                if (operand instanceof SqlTableIdentifier tableId) {
                    DatabaseEntity tableEntity = tableId.getEntity();
                    if (tableEntity != null) {
                        Collection<Column> columns = null;
                        if (tableEntity instanceof Table table) {
                            columns = table.getColumns();
                        } else if (tableEntity instanceof View view) {
                            columns = view.getColumns();
                        }

                        if (columns != null) {
                            for (Column col : columns) {
                                String colName = col.getName() != null ? col.getName().getName().toLowerCase() : null;
                                if (colName == null) {
                                    continue;
                                }
                                SqlIdentifier columnNode = new SqlIdentifier(Arrays.asList(colName), SqlParserPos.ZERO);
                                expanded.add(new SubqueryInfo.ColumnProjection(colName, columnNode, tableEntity, col));
                            }
                        }
                        return expanded;
                    }
                }
                // Check if any operand is a subquery
                else if (operand instanceof SqlSelect subqueryOperand) {
                    List<SubqueryInfo.ColumnProjection> nestedColumns = extractProjectedColumns(subqueryOperand);
                    for (SubqueryInfo.ColumnProjection col : nestedColumns) {
                        // Skip if this is a window function result (e.g., ROW_NUMBER, RANK, etc.)
                        if (isWindowFunctionColumn(col.getExpression())) {
                            continue;
                        }
                        expanded.add(col);
                    }
                    return expanded;
                }
                // Recursively handle nested SqlBasicCall (e.g., WHERE clauses wrapping subqueries)
                else if (operand instanceof SqlBasicCall nestedCall) {
                    // Recursively call expandWildcardFromFromClause with a synthetic SqlSelect
                    // wrapping the nested call to extract columns
                    List<SubqueryInfo.ColumnProjection> nestedColumns = expandWildcardFromFromClause(syntheticSelectFrom(nestedCall));
                    for (SubqueryInfo.ColumnProjection col : nestedColumns) {
                        // Skip if this is a window function result (e.g., ROW_NUMBER, RANK, etc.)
                        if (isWindowFunctionColumn(col.getExpression())) {
                            continue;
                        }
                        expanded.add(col);
                    }
                    if (!expanded.isEmpty()) {
                        return expanded;
                    }
                }
            }
        }
        // If the FROM is a JOIN operation
        else if (from instanceof SqlJoin join) {
            // Recursively extract columns from both sides of the join
            List<SubqueryInfo.ColumnProjection> leftColumns = expandWildcardFromFromClause(syntheticSelectFrom(join.getLeft()));
            List<SubqueryInfo.ColumnProjection> rightColumns = expandWildcardFromFromClause(syntheticSelectFrom(join.getRight()));
            expanded.addAll(leftColumns);
            expanded.addAll(rightColumns);
        }

        return expanded;
    }

    /**
     * Creates a synthetic SqlSelect with the given FROM node for recursive wildcard expansion.
     */
    private SqlSelect syntheticSelectFrom(SqlNode from) {
        // Create a minimal SqlSelect just to pass the FROM node to expandWildcardFromFromClause
        return new SqlSelect(
                SqlParserPos.ZERO,
                SqlNodeList.EMPTY, // hints
                SqlNodeList.EMPTY, // selectList (not used)
                from,
                null, // where (not used)
                null, // groupBy (not used)
                null, // having (not used)
                null, // window
                null, // qualify
                null, // orderBy (not used)
                null, // offset (not used)
                null, // fetch (not used)
                null  // hints (second one)
        );
    }

    /**
     * Tracks all aliases from the FROM clause of a SELECT query.
     * This must be called before processing wildcards to ensure aliases are registered.
     */
    private void trackAliasesFromFromClause(SqlSelect subquery) {
        if (subquery == null) {
            return;
        }

        SqlNode fromNode = subquery.getFrom();
        if (fromNode == null) {
            return;
        }

        // Handle different FROM clause structures
        if (fromNode instanceof SqlBasicCall basicCall) {
            // Could be "table AS alias" or JOIN with "table AS alias"
            String operatorName = basicCall.getOperator() != null ? basicCall.getOperator().getName() : null;

            if (operatorName != null && operatorName.equalsIgnoreCase("AS") && basicCall.getOperandList().size() == 2) {
                // "table AS alias" pattern
                SqlNode tableNode = basicCall.getOperandList().get(0);
                SqlNode aliasNode = basicCall.getOperandList().get(1);

                if (aliasNode instanceof SqlIdentifier aliasId) {
                    String alias = aliasId.getSimple();
                    trackTableAlias(tableNode, alias);
                }
            }
        } else if (fromNode instanceof SqlJoin) {
            // DO NOT track aliases from JOINs here - they are already handled by visitJoin/visitFrom
            // This prevents nested JOIN aliases from overwriting parent scope aliases
        } else if (fromNode instanceof SqlIdentifier) {
            // Direct table reference without alias
            // Don't need to track anything
        } else if (fromNode instanceof SqlSelect) {
            // Subquery in FROM clause
            // Already handled by trackTableAlias when the alias is processed
        }
    }

    /**
     * Expands a qualified wildcard (e.g., a.*) from a specific table/alias in the FROM clause.
     */
    private List<SubqueryInfo.ColumnProjection> expandWildcardFromTable(SqlSelect subquery, String tableAlias) {
        List<SubqueryInfo.ColumnProjection> expanded = new ArrayList<>();

        // First try to resolve the table alias as a CTE/subquery directly
        if (scopeManager.hasSubquery(tableAlias)) {
            SubqueryInfo subqueryInfo = scopeManager.getSubqueryInfo(tableAlias);
            if (subqueryInfo != null) {
                // Return the column projections from the subquery/CTE
                List<SubqueryInfo.ColumnProjection> cols = subqueryInfo.getColumnProjections();
                expanded.addAll(cols);
                return expanded;
            }
        }

        // If not found, try to resolve the alias to its actual table/CTE
        SqlIdentifier tableRef = scopeManager.getTableByAlias(tableAlias);
        if (tableRef != null && tableRef.names.size() > 0) {
            String actualTableName = tableRef.names.get(tableRef.names.size() - 1);

            // Try to get columns from the actual table/CTE
            if (scopeManager.hasSubquery(actualTableName)) {
                SubqueryInfo subqueryInfo = scopeManager.getSubqueryInfo(actualTableName);
                if (subqueryInfo != null) {
                    List<SubqueryInfo.ColumnProjection> cols = subqueryInfo.getColumnProjections();
                    expanded.addAll(cols);
                    return expanded;
                }
            }
        }

        return expanded;
    }

    /**
     * Helper class to hold source information extracted from expressions
     */
    private static class SourceInfo {
        final Column sourceColumn;
        final DatabaseEntity sourceEntity;

        SourceInfo(Column sourceColumn, DatabaseEntity sourceEntity) {
            this.sourceColumn = sourceColumn;
            this.sourceEntity = sourceEntity;
        }
    }

    /**
     * Recursively extracts source column information from an expression.
     * Handles aggregates, function calls, and nested expressions.
     */
    private SourceInfo extractSourceFromExpression(SqlNode expression) {
        if (expression == null) {
            return null;
        }

        // Direct column reference
        if (expression instanceof SqlColumnIdentifier columnId) {
            return new SourceInfo(columnId.getEntity(), columnId.getDatabaseEntity());
        }

        // Handle SqlCase - extract source from WHEN conditions
        if (expression instanceof SqlCase sqlCase) {
            // Check WHEN clauses for column references
            if (sqlCase.getWhenOperands() != null) {
                for (SqlNode whenOperand : sqlCase.getWhenOperands()) {
                    SourceInfo sourceInfo = extractSourceFromExpression(whenOperand);
                    if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                        return sourceInfo;
                    }
                }
            }
            // Check THEN values
            if (sqlCase.getThenOperands() != null) {
                for (SqlNode thenOperand : sqlCase.getThenOperands()) {
                    SourceInfo sourceInfo = extractSourceFromExpression(thenOperand);
                    if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                        return sourceInfo;
                    }
                }
            }
            // Check ELSE clause
            if (sqlCase.getElseOperand() != null) {
                SourceInfo sourceInfo = extractSourceFromExpression(sqlCase.getElseOperand());
                if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                    return sourceInfo;
                }
            }
            // If case has value operand (like CASE col WHEN ...)
            if (sqlCase.getValueOperand() != null) {
                SourceInfo sourceInfo = extractSourceFromExpression(sqlCase.getValueOperand());
                if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                    return sourceInfo;
                }
            }
        }

        // Function call or operation - recursively check operands
        if (expression instanceof SqlBasicCall call) {
            for (SqlNode operand : call.getOperandList()) {
                SourceInfo sourceInfo = extractSourceFromExpression(operand);
                if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                    // Return the first source column we find
                    return sourceInfo;
                }
            }
        }

        // Handle SqlNodeList (e.g., IN clauses, parameter lists)
        if (expression instanceof SqlNodeList nodeList) {
            for (SqlNode node : nodeList) {
                SourceInfo sourceInfo = extractSourceFromExpression(node);
                if (sourceInfo != null && sourceInfo.sourceColumn != null) {
                    // Return the first source column we find
                    return sourceInfo;
                }
            }
        }

        return null;
    }

    /**
     * Checks if an expression is a window function (e.g., ROW_NUMBER, RANK, DENSE_RANK, etc.).
     * This is used to filter out internal window function results during wildcard expansion.
     */
    private boolean isWindowFunctionColumn(SqlNode expression) {
        if (expression == null) {
            return false;
        }

        // Handle AS operations - check the underlying expression
        if (expression instanceof SqlBasicCall basicCall &&
                basicCall.getOperator() != null &&
                basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                basicCall.getOperandList().size() >= 1) {
            SqlNode underlyingExpr = basicCall.getOperandList().get(0);
            return isWindowFunctionColumn(underlyingExpr);
        }

        // Check for window function calls (e.g., ROW_NUMBER() OVER (...))
        if (expression instanceof SqlBasicCall basicCall && basicCall.getOperator() != null) {
            String functionName = basicCall.getOperator().getName();

            // Common window functions that should be filtered
            return functionName.equalsIgnoreCase("ROW_NUMBER") ||
                    functionName.equalsIgnoreCase("RANK") ||
                    functionName.equalsIgnoreCase("DENSE_RANK") ||
                    functionName.equalsIgnoreCase("PERCENT_RANK") ||
                    functionName.equalsIgnoreCase("CUME_DIST") ||
                    functionName.equalsIgnoreCase("NTILE") ||
                    functionName.equalsIgnoreCase("LAG") ||
                    functionName.equalsIgnoreCase("LEAD") ||
                    functionName.equalsIgnoreCase("FIRST_VALUE") ||
                    functionName.equalsIgnoreCase("LAST_VALUE");
        }

        return false;
    }

    // Scope management methods

    /**
     * Scope management with automatic cleanup
     */
    public interface ScopeContext extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * Creates a new scope context for safe scope management
     */
    private ScopeContext createScopeContext() {
        scopeManager.pushScope();
        return scopeManager::popScope;
    }

    /**
     * Collects table aliases from a SELECT statement before processing column references
     */
    private void collectTableAliasesFromSelect(SqlSelect select) {
        if (select.getFrom() != null) {
            collectTableAliasesFromNode(select.getFrom());
        }
    }

    /**
     * Recursively collects table aliases from SQL nodes
     */
    private void collectTableAliasesFromNode(SqlNode node) {
        if (node instanceof SqlJoin join) {
            collectTableAliasesFromNode(join.getLeft());
            collectTableAliasesFromNode(join.getRight());
        } else if (node instanceof SqlBasicCall call &&
                call.getOperator() != null &&
                call.getOperator().getName().equalsIgnoreCase("AS") &&
                call.getOperandList().size() == 2) {

            SqlNode left = call.getOperandList().get(0);
            SqlNode right = call.getOperandList().get(1);

            if (left instanceof SqlIdentifier leftId && right instanceof SqlIdentifier rightId) {
                String alias = rightId.getSimple();
                if (alias != null) {
                    // Qualify the left operand to get the actual table/view
                    SqlNode qualifiedLeft = qualifyTableIdentifier(createTableIdentifierFromSqlIdentifier(leftId));

                    if (qualifiedLeft instanceof SqlTableIdentifier qualifiedTable) {
                        scopeManager.addTableAlias(alias, qualifiedTable);
                        log.debug("Pre-registered table alias {} -> {}", alias, String.join(".", qualifiedTable.names));
                    } else if (qualifiedLeft instanceof SqlViewIdentifier qualifiedView) {
                        scopeManager.addTableAlias(alias, qualifiedView);
                        log.debug("Pre-registered view alias {} -> {}", alias, String.join(".", qualifiedView.names));
                    }
                }
            }
        } else if (node instanceof SqlIdentifier identifier) {
            // Handle unaliased table/view - might have implicit alias
            SqlTableIdentifier tableId = createTableIdentifierFromSqlIdentifier(identifier);
            SqlNode qualifiedNode = qualifyTableIdentifier(tableId);

            if (qualifiedNode instanceof SqlTableIdentifier qualifiedTable) {
                scopeManager.addTable(qualifiedTable);
                // For unaliased tables, the table name itself can be used as an alias
                String tableName = qualifiedTable.getTableName();
                if (tableName != null) {
                    scopeManager.addTableAlias(tableName, qualifiedTable);
                }
            } else if (qualifiedNode instanceof SqlViewIdentifier qualifiedView) {
                scopeManager.addTable(qualifiedView);
                // For unaliased views, the view name itself can be used as an alias
                String viewName = qualifiedView.getViewName();
                if (viewName != null) {
                    scopeManager.addTableAlias(viewName, qualifiedView);
                }
            }
        }
    }

    /**
     * Creates a SqlTableIdentifier from a SqlIdentifier
     */
    private SqlTableIdentifier createTableIdentifierFromSqlIdentifier(SqlIdentifier identifier) {
        return new SqlTableIdentifier(
                identifier.names,
                identifier.getParserPosition(),
                null // Entity will be resolved during qualification
        );
    }

    /**
     * Qualifies a SqlTableIdentifier using EntityResolver to determine if it's a table or view,
     * and returns the appropriate qualified identifier with the entity attached
     */
    private SqlIdentifier qualifyTableIdentifier(SqlTableIdentifier tableId) {
        return tableQualifier.qualifyTableIdentifier(tableId);
    }

    /**
     * Creates a Column entity from a table/view entity and column name
     */
    private Column createColumnEntity(DatabaseEntity tableEntity, String columnName) {
        if (tableEntity == null || columnName == null) {
            return null;
        }

        try {
            // Create column namespace from table entity
            List<Identifier> columnNamespace = new ArrayList<>();
            if (tableEntity.getNamespace() != null) {
                columnNamespace.addAll(tableEntity.getNamespace());
            }
            columnNamespace.add(tableEntity.getName());

            // Create the column entity
            return Column.builder()
                    .namespace(columnNamespace)
                    .name(Identifier.of(columnName, this.dialect))
                    .dialect(this.dialect)
                    .ordinalPosition(0) // We don't have ordinal position info, use 0
                    .type(DataType.create(org.apache.calcite.sql.type.SqlTypeName.VARCHAR)) // Default type, could be enhanced
                    .build();
        } catch (Exception e) {
            log.warn("Failed to create column entity for {}.{}: {}",
                    tableEntity.getName(), columnName, e.getMessage());
            return null;
        }
    }

    private void extractAndRegisterComputedColumns(SqlNodeList selectList) {
        if (selectList == null) {
            return;
        }

        for (SqlNode selectItem : selectList) {
            if (selectItem instanceof SqlBasicCall call &&
                    call.getOperator() != null &&
                    call.getOperator().getName().equalsIgnoreCase("AS") &&
                    call.getOperandList().size() == 2) {

                SqlNode expression = call.getOperandList().get(0);
                SqlNode aliasNode = call.getOperandList().get(1);

                if (aliasNode instanceof SqlIdentifier aliasId) {
                    String alias = aliasId.getSimple();
                    if (alias != null) {
                        ComputedColumnInfo columnInfo = new ComputedColumnInfo(alias, expression);
                        scopeManager.addComputedColumn(alias, columnInfo);
                    }
                }
            }
        }
    }

    private SqlNode resolveOrdinalIfNeeded(SqlNode node, SqlNodeList selectList, SqlOrdinalReference.OrdinalContext context) {
        if (node instanceof SqlLiteral literal) {
            if (literal.getTypeName() == org.apache.calcite.sql.type.SqlTypeName.DECIMAL ||
                    literal.getTypeName() == org.apache.calcite.sql.type.SqlTypeName.INTEGER) {

                try {
                    int ordinalValue = literal.intValue(false);

                    if (ordinalValue >= 1 && ordinalValue <= selectList.size()) {
                        int zeroBasedIndex = ordinalValue - 1;
                        SqlNode referencedItem = selectList.get(zeroBasedIndex);

                        log.debug("Resolved {} ordinal {} to SELECT item: {}",
                                context, ordinalValue, referencedItem);

                        return new SqlOrdinalReference(
                                ordinalValue,
                                referencedItem,
                                context,
                                literal.getParserPosition()
                        );
                    } else {
                        log.warn("{} ordinal {} is out of range (SELECT list has {} items)",
                                context, ordinalValue, selectList.size());
                    }
                } catch (Exception e) {
                    log.debug("Failed to resolve ordinal: {}", e.getMessage());
                }
            }
        }

        return null;
    }
}
