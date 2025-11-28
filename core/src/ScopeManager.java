package com.calcite_new.sql.core.processor.visitor.scope;

import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;

import java.util.*;
import java.util.function.Supplier;

/**
 * Manages query scopes for nested SQL queries and table alias tracking.
 * Provides scope isolation for subqueries while maintaining access to outer scope tables.
 */
@Slf4j
public class ScopeManager {

    // Stack-based scope management for nested queries
    private final Deque<QueryScope> scopeStack = new ArrayDeque<>();

    public ScopeManager() {
        // Initialize with root scope
        scopeStack.push(new QueryScope());
    }

    /**
     * Executes the given operation within a new scope context.
     * The scope is automatically cleaned up after execution.
     */
    public <T> T executeInScope(Supplier<T> operation) {
        pushScope();
        try {
            return operation.get();
        } finally {
            popScope();
        }
    }

    /**
     * Executes the given operation within a new scope context that inherits from current scope.
     */
    public <T> T executeInInheritedScope(Supplier<T> operation) {
        pushScope();
        try {
            return operation.get();
        } finally {
            popScope();
        }
    }

    /**
     * Pushes a new scope onto the stack, inheriting from the current scope.
     */
    public void pushScope() {
        QueryScope currentScope = getCurrentScope();
        QueryScope newScope = currentScope != null ? currentScope.copy() : new QueryScope();
        scopeStack.push(newScope);
        log.debug("Pushed new scope, total scopes: {}", scopeStack.size());
    }

    /**
     * Pops the current scope from the stack.
     */
    public void popScope() {
        if (scopeStack.size() > 1) {
            scopeStack.pop();
            log.debug("Popped scope, remaining scopes: {}", scopeStack.size());
        }
    }

    /**
     * Gets the current scope.
     */
    public QueryScope getCurrentScope() {
        return scopeStack.peek();
    }

    /**
     * Gets the current scope depth (stack size).
     */
    public int getScopeDepth() {
        return scopeStack.size();
    }

    /**
     * Adds a table alias to the current scope.
     */
    public void addTableAlias(String alias, SqlIdentifier table) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        Objects.requireNonNull(table, "Table cannot be null");

        getCurrentScope().addTableAlias(alias, table);
        log.debug("Added table alias: {} -> {}", alias, getTableName(table));
    }

    /**
     * Adds a table to the current scope.
     */
    public void addTable(SqlIdentifier table) {
        Objects.requireNonNull(table, "Table cannot be null");
        getCurrentScope().addTable(table);
    }

    /**
     * Gets a table by its alias from the current scope.
     */
    public SqlIdentifier getTableByAlias(String alias) {
        return alias != null ? getCurrentScope().getTableByAlias(alias) : null;
    }

    /**
     * Gets a table by its name from the current scope.
     */
    public SqlIdentifier getTableByName(String tableName) {
        return tableName != null ? getCurrentScope().getTableByName(tableName) : null;
    }

    /**
     * Gets all available tables in the current scope.
     */
    public Set<SqlIdentifier> getAvailableTables() {
        return getCurrentScope().getAvailableTables();
    }

    /**
     * Adds subquery information to the current scope.
     */
    public void addSubqueryInfo(String alias, SubqueryInfo subqueryInfo) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        Objects.requireNonNull(subqueryInfo, "SubqueryInfo cannot be null");
        log.debug("Adding subquery info for alias '{}' with {} columns", alias, subqueryInfo.getColumnProjections().size());
        getCurrentScope().addSubqueryInfo(alias, subqueryInfo);
    }

    /**
     * Gets subquery information by alias from the current scope.
     */
    public SubqueryInfo getSubqueryInfo(String alias) {
        return alias != null ? getCurrentScope().getSubqueryInfo(alias) : null;
    }

    /**
     * Checks if a subquery exists in the current scope.
     */
    public boolean hasSubquery(String alias) {
        boolean hasSubquery = alias != null && getCurrentScope().hasSubquery(alias);
        log.debug("Checking for subquery '{}': {}", alias, hasSubquery);
        if (hasSubquery) {
            SubqueryInfo info = getSubqueryInfo(alias);
            log.debug("Subquery '{}' found with {} columns", alias,
                    info != null ? info.getColumnProjections().size() : 0);
        }
        return hasSubquery;
    }

    /**
     * Gets all subquery aliases in the current scope.
     */
    public Set<String> getSubqueryAliases() {
        return getCurrentScope().getAvailableSubqueries().keySet();
    }

    /**
     * Adds expanded wildcard information to the current scope.
     */
    public void addExpandedWildcard(SqlIdentifier wildcard, List<SqlNode> expandedColumns) {
        Objects.requireNonNull(wildcard, "Wildcard cannot be null");
        Objects.requireNonNull(expandedColumns, "Expanded columns cannot be null");
        getCurrentScope().addExpandedWildcard(wildcard, expandedColumns);
    }

    /**
     * Gets expanded wildcard information from the current scope.
     */
    public List<SqlNode> getExpandedWildcard(SqlIdentifier wildcard) {
        return wildcard != null ? getCurrentScope().getExpandedWildcard(wildcard) : null;
    }

    /**
     * Checks if a wildcard has been expanded in the current scope.
     */
    public boolean hasExpandedWildcard(SqlIdentifier wildcard) {
        return wildcard != null && getCurrentScope().hasExpandedWildcard(wildcard);
    }

    /**
     * Adds computed column information to the current scope.
     */
    public void addComputedColumn(String alias, ComputedColumnInfo computedColumnInfo) {
        Objects.requireNonNull(alias, "Alias cannot be null");
        Objects.requireNonNull(computedColumnInfo, "ComputedColumnInfo cannot be null");
        getCurrentScope().addComputedColumn(alias, computedColumnInfo);
    }

    /**
     * Gets computed column information by alias from the current scope.
     */
    public ComputedColumnInfo getComputedColumn(String alias) {
        return alias != null ? getCurrentScope().getComputedColumn(alias) : null;
    }

    /**
     * Checks if a computed column exists in the current scope.
     */
    public boolean hasComputedColumn(String alias) {
        return alias != null && getCurrentScope().hasComputedColumn(alias);
    }

    public void setSelectListItems(List<SqlNode> items) {
        getCurrentScope().setSelectListItems(items);
    }

    public List<SqlNode> getSelectListItems() {
        return getCurrentScope().getSelectListItems();
    }

    public SqlNode getSelectListItem(int ordinal) {
        return getCurrentScope().getSelectListItem(ordinal);
    }

    public void addNestedBinding(String alias, com.calcite_new.core.model.entity.DatabaseEntity owner, String baseToken) {
        getCurrentScope().addNestedBinding(alias, owner, baseToken);
    }

    public com.calcite_new.core.model.entity.DatabaseEntity getNestedOwner(String alias) {
        return getCurrentScope().getNestedOwner(alias);
    }

    public String getNestedBaseToken(String alias) {
        return getCurrentScope().getNestedBaseToken(alias);
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
}
