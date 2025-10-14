package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.sql.SqlMerge;
import com.calcite_new.sql.SqlUpdate;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;
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
import java.util.stream.Collectors;

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

        // Initialize scope manager first as it's needed by other components
        this.scopeManager = new ScopeManager();
        this.dialect = dialect;

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
                // Table alias - add to scope for future reference
                scopeManager.addTableAlias(alias, tableId);
                log.debug("Added table alias: {} -> {}", alias, getTableName(tableId));
            } else if (left instanceof SqlViewIdentifier viewId) {
                // View alias - add to scope for future reference
                scopeManager.addTableAlias(alias, viewId);
                log.debug("Added view alias: {} -> {}", alias, getTableName(viewId));
            } else if (left instanceof SqlSelect subquery) {
                // Subquery alias - create virtual table and add column projections
                List<SubqueryInfo.ColumnProjection> projectedColumns = extractProjectedColumns(subquery);
                SubqueryInfo subqueryInfo = new SubqueryInfo(subquery, projectedColumns);
                scopeManager.addSubqueryInfo(alias, subqueryInfo);
                log.debug("Added subquery alias: {} with projected columns: {}", alias,
                        projectedColumns.stream().map(SubqueryInfo.ColumnProjection::getColumnName).collect(Collectors.joining(", ")));
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
            SqlNodeList targetColumnList = visitNodeList(update.getTargetColumnList());
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

    // Visit methods for standard Calcite SQL statement types

    private SqlSelect visitSelect(SqlSelect select) {
        try (ScopeContext scope = createScopeContext()) {
            // Process FROM clause first to establish table aliases
            SqlNode from = visitFrom(select.getFrom());

            // Now process SELECT list with aliases available
            SqlNodeList selectList = visitSelectList(select.getSelectList());
            extractAndRegisterComputedColumns(selectList);
            scopeManager.setSelectListItems(selectList.getList());
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
        // Process each CTE definition and register it in scope
        List<SqlNode> processedWithItems = new ArrayList<>();

        if (with.withList != null) {
            for (SqlNode withItem : with.withList) {
                if (withItem instanceof SqlWithItem cteItem) {
                    String cteName = cteItem.name != null ? cteItem.name.getSimple() : null;

                    // Process the CTE query
                    SqlNode processedQuery = accept(cteItem.query);

                    // CRITICAL: Unwrap SqlOrderBy to get to the underlying SELECT
                    SqlSelect selectQuery = null;
                    if (processedQuery instanceof SqlSelect) {
                        selectQuery = (SqlSelect) processedQuery;
                    } else if (processedQuery instanceof SqlOrderBy orderBy) {
                        // ORDER BY/LIMIT wraps the SELECT - unwrap it
                        if (orderBy.query instanceof SqlSelect) {
                            selectQuery = (SqlSelect) orderBy.query;
                        }
                    }

                    // Extract projected columns and register the CTE
                    if (selectQuery != null && cteName != null) {
                        List<SubqueryInfo.ColumnProjection> projectedColumns = extractProjectedColumns(selectQuery);
                        SubqueryInfo cteInfo = new SubqueryInfo(selectQuery, projectedColumns);
                        scopeManager.addSubqueryInfo(cteName, cteInfo);

                        log.debug("Registered CTE '{}' with {} projected columns", cteName, projectedColumns.size());
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
     * Processes the SELECT list, handling wildcard expansion.
     */
    @SuppressWarnings("unused")
    private SqlNodeList processSelectList(SqlNodeList selectList) {
        if (selectList == null || selectList.isEmpty()) {
            return selectList;
        }

        List<SqlNode> newSelectList = new ArrayList<>();
        for (SqlNode item : selectList) {
            if (item instanceof SqlIdentifier identifier && identifier.isStar()) {
                // Handle unqualified wildcard (e.g., *) or qualified (e.g., table.*)
                newSelectList.addAll(wildcardExpander.expandWildcardToList(identifier));
            } else if (item instanceof SqlBasicCall call && call.getOperator().kind == SqlKind.AS) {
                // Handle aliased expressions, e.g., (expr) AS alias or table.* AS alias
                SqlNode left = call.getOperandList().get(0);

                if (left instanceof SqlIdentifier leftId && leftId.isStar()) {
                    // Handle table.* AS alias pattern
                    newSelectList.addAll(wildcardExpander.expandWildcardToList(leftId));
                } else {
                    // Regular aliased expression, just visit the left part
                    newSelectList.add(item.accept(this));
                }
            } else {
                // Regular select item, just visit it
                newSelectList.add(item.accept(this));
            }
        }
        return new SqlNodeList(newSelectList, selectList.getParserPosition());
    }

    /**
     * Handles SqlBasicCall operations by visiting their operands.
     */
    private SqlNode visitBasicCall(SqlBasicCall basicCall) {
        // Visit all operands
        List<SqlNode> newOperands = new ArrayList<>();
        for (SqlNode operand : basicCall.getOperandList()) {
            newOperands.add(operand.accept(this));
        }

        // Create new SqlBasicCall with visited operands
        return new SqlBasicCall(basicCall.getOperator(), newOperands, basicCall.getParserPosition());
    }

    // Visit helper methods

    private SqlNode visitFrom(SqlNode from) {
        if (from == null) {
            return null;
        }

        if (from instanceof SqlJoin join) {
            return visitJoin(join);
        } else if (from instanceof SqlIdentifier tableId) {
            // Handle table/view references with potential aliases
            SqlNode processedTable = accept(tableId);
            if (processedTable instanceof SqlTableIdentifier processedTableId) {
                scopeManager.addTable(processedTableId);
            } else if (processedTable instanceof SqlViewIdentifier processedViewId) {
                scopeManager.addTable(processedViewId);
            }
            return processedTable;
        } else if (from instanceof SqlBasicCall basicCall) {
            // Process all operands of SqlBasicCall to ensure table references are qualified
            SqlNode processedCall = visitBasicCall(basicCall);

            // Handle table with alias (e.g., table AS alias or table alias) after processing
            if (basicCall.getOperator() != null &&
                    basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                    basicCall.getOperandList().size() == 2) {
                SqlNode left = accept(basicCall.getOperandList().get(0));
                SqlNode right = basicCall.getOperandList().get(1);

                if (left instanceof SqlTableIdentifier && right instanceof SqlIdentifier) {
                    String alias = ((SqlIdentifier) right).getSimple();
                    trackTableAlias(left, alias);
                }
            }

            return processedCall;
        }
        return accept(from);
    }

    private SqlNode visitJoin(SqlJoin join) {
        // Process left side
        SqlNode left = join.getLeft().accept(this);
        trackTableFromNode(left);

        // Process right side
        SqlNode right = join.getRight().accept(this);
        trackTableFromNode(right);

        // Process condition
        SqlNode condition = join.getCondition() != null ?
                join.getCondition().accept(this) : null;

        // Create new join with processed operands
        return new SqlJoin(
                join.getParserPosition(),
                left,
                join.isNaturalNode(),
                join.getJoinTypeNode(),
                right,
                join.getConditionTypeNode(),
                condition
        );
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
     * Tracks table/view aliases from FROM clauses without AS keyword
     */
    private void trackTableAlias(SqlNode tableNode, String alias) {
        if (tableNode == null || alias == null) {
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
                    log.debug("Registered alias '{}' for CTE '{}'", alias, tableName);
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
                    log.debug("Registered alias '{}' for CTE '{}'", alias, tableName);
                }
                return;
            }

            // Regular table tracking
            SqlTableIdentifier trackedTable = new SqlTableIdentifier(
                    tableIdentifier.names,
                    tableIdentifier.getParserPosition(),
                    null // Entity will be resolved later if needed
            );
            scopeManager.addTableAlias(alias, trackedTable);
        }
    }

    /**
     * Tracks tables from join operands
     */
    private void trackTableFromNode(SqlNode node) {
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
            // Handle table/view AS alias pattern
            SqlNode tableNode = basicCall.getOperandList().get(0);
            SqlNode aliasNode = basicCall.getOperandList().get(1);

            if (tableNode instanceof SqlTableIdentifier tableId) {
                String tableName = tableId.getTableName();
                if (tableName != null && scopeManager.hasSubquery(tableName)) {
                    // Don't add it as a table - it's a CTE
                    return;
                }
            }

            if ((tableNode instanceof SqlTableIdentifier || tableNode instanceof SqlViewIdentifier) &&
                    aliasNode instanceof SqlIdentifier) {
                String alias = ((SqlIdentifier) aliasNode).getSimple();
                scopeManager.addTableAlias(alias, (SqlIdentifier) tableNode);
            }
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

        // Handle AS operations with wildcards
        if (selectItem instanceof SqlBasicCall basicCall &&
                basicCall.getOperator() != null &&
                basicCall.getOperator().getName().equalsIgnoreCase("AS") &&
                basicCall.getOperandList().size() == 2) {

            SqlNode leftOperand = basicCall.getOperandList().get(0);
            SqlNode rightOperand = basicCall.getOperandList().get(1);

            if (leftOperand instanceof SqlIdentifier leftId && leftId.isStar()) {
                // Handle table.* AS alias pattern
                return wildcardExpander.expandWildcardWithAliasToList(leftId, rightOperand);
            }
        }

        // Process other select items normally
        SqlNode processedItem = accept(selectItem);
        return List.of(processedItem);
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

        return null;
    }

    /**
     * Extracts projected columns from a subquery's SELECT list.
     * This is a simplified implementation. A full implementation would need to handle
     * complex expressions, aliases within the SELECT list, and wildcards.
     */
    private List<SubqueryInfo.ColumnProjection> extractProjectedColumns(SqlSelect subquery) {
        if (subquery.getSelectList() == null) {
            return List.of();
        }
        List<SubqueryInfo.ColumnProjection> projected = new ArrayList<>();
        for (SqlNode item : subquery.getSelectList()) {
            if (item instanceof SqlIdentifier id) {
                // Handle both single and multi-part identifiers
                String columnName;
                Column sourceColumn = null;
                DatabaseEntity sourceEntity = null;

                // If it's a SqlColumnIdentifier, we can get the source column
                if (id instanceof SqlColumnIdentifier columnId) {
                    sourceColumn = columnId.getEntity();
                    sourceEntity = columnId.getDatabaseEntity();
                    columnName = columnId.getColumnName();
                } else {
                    // For regular SqlIdentifier, handle both single and multi-part cases
                    if (id.names.size() == 1) {
                        columnName = id.getSimple();
                    } else {
                        // Multi-part identifier - use the last part as column name
                        columnName = id.names.get(id.names.size() - 1);
                    }
                }
                projected.add(new SubqueryInfo.ColumnProjection(columnName, id, sourceEntity, sourceColumn));
            } else if (item instanceof SqlBasicCall call && call.getOperator().kind == SqlKind.AS) {
                // If it's an aliased column, use the alias
                if (call.getOperandList().size() > 1 && call.getOperandList().get(1) instanceof SqlIdentifier aliasId) {
                    String aliasName;
                    if (aliasId.names.size() == 1) {
                        aliasName = aliasId.getSimple();
                    } else {
                        aliasName = aliasId.names.get(aliasId.names.size() - 1);
                    }

                    // Extract source column from the expression if it's a SqlColumnIdentifier
                    SqlNode expression = call.getOperandList().get(0);
                    Column sourceColumn = null;
                    DatabaseEntity sourceEntity = null;
                    if (expression instanceof SqlColumnIdentifier columnId) {
                        sourceColumn = columnId.getEntity();
                        sourceEntity = columnId.getDatabaseEntity();
                    }

                    projected.add(new SubqueryInfo.ColumnProjection(aliasName, expression, sourceEntity, sourceColumn));
                }
            }
        }
        return projected;
    }

    /**
     * Helper method to extract table/view name from SqlIdentifier.
     */
    private String getTableName(SqlIdentifier identifier) {
        if (identifier == null) {
            return null;
        }

        if (identifier instanceof SqlTableIdentifier tableId) {
            return tableId.getTableName();
        } else if (identifier instanceof SqlViewIdentifier viewId) {
            return viewId.getViewName();
        } else if (identifier.names != null && !identifier.names.isEmpty()) {
            return identifier.names.get(identifier.names.size() - 1);
        }
        return null;
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
                    .name(Identifier.of(columnName, dialect))
                    .dialect(dialect)
                    .ordinalPosition(0) // We don't have ordinal position info, use 0
                    .type(DataType.create(org.apache.calcite.sql.type.SqlTypeName.VARCHAR)) // Default type, could be enhanced
                    .build();
        } catch (Exception e) {
            log.warn("Failed to create column entity for {}.{}: {}",
                    tableEntity.getName().getName(), columnName, e.getMessage());
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

                        String type = columnInfo.isSimpleColumnAlias() ? "simple column alias" : "computed column alias";
                        log.debug("Registered {} : {} -> {} (type: {})",
                                type, alias, expression, columnInfo.getExpressionType());
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


