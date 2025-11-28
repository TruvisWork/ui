package com.calcite_new.sql.core.processor.visitor.scope;

import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;

import java.util.*;

/**
 * Represents a query scope that tracks available tables, aliases, CTEs, and subqueries.
 * Each scope maintains its own mappings and can be copied for inheritance.
 */
@Slf4j
public class QueryScope {

    // Maps alias -> qualified table/view identifier with entity
    private final Map<String, SqlIdentifier> aliasToTableMap = new HashMap<>();

    // Maps table/view name -> qualified table/view identifier (for unaliased tables/views)
    private final Map<String, SqlIdentifier> tableNameMap = new HashMap<>();

    // Ordered set of available table/view identifiers in this scope (insertion order preserved)
    private final LinkedHashSet<SqlIdentifier> availableTables = new LinkedHashSet<>();

    // CTE definitions in this scope
    private final Map<String, SqlNode> cteDefinitions = new HashMap<>();

    // Subquery information for subquery aliases
    private final Map<String, SubqueryInfo> subqueryInfoMap = new HashMap<>();

    // Expanded wildcard information
    private final Map<SqlIdentifier, List<SqlNode>> expandedWildcards = new HashMap<>();

    // Computed column aliases from SELECT list (e.g., ROW_NUMBER() OVER (...) AS row_num)
    private final Map<String, ComputedColumnInfo> computedColumnMap = new HashMap<>();

    // Learned nested alias bindings: alias -> owner entity and base token (e.g., rules -> (rules_audit, workflow))
    private final Map<String, com.calcite_new.core.model.entity.DatabaseEntity> nestedOwnerMap = new HashMap<>();
    private final Map<String, String> nestedBaseTokenMap = new HashMap<>();

    // SELECT list items for ordinal resolution (GROUP BY 1, ORDER BY 2, etc.)
    private List<SqlNode> selectListItems = new ArrayList<>();

    /**
     * Adds a table alias to this scope.
     */
    public void addTableAlias(String alias, SqlIdentifier table) {
        if (alias == null || table == null) {
            log.warn("Attempted to add null table alias or table: alias={}, table={}", alias, table);
            return;
        }
        aliasToTableMap.put(alias.toLowerCase(), table);
        availableTables.add(table);
        String tableName = getTableName(table);
        log.debug("Added table/view alias {} -> {}", alias, tableName);
    }

    /**
     * Adds a table to this scope.
     */
    public void addTable(SqlIdentifier table) {
        if (table == null) {
            log.warn("Attempted to add null table");
            return;
        }
        String tableName = getTableName(table);
        if (tableName != null) {
            tableNameMap.put(tableName.toLowerCase(), table);
            availableTables.add(table);
        }
    }

    /**
     * Adds a CTE definition to this scope.
     */
    public void addCTE(String name, SqlNode definition) {
        if (name != null && definition != null) {
            cteDefinitions.put(name.toLowerCase(), definition);
        }
    }

    /**
     * Adds subquery information to this scope.
     */
    public void addSubqueryInfo(String alias, SubqueryInfo subqueryInfo) {
        if (alias != null && subqueryInfo != null) {
            subqueryInfoMap.put(alias.toLowerCase(), subqueryInfo);
        }
    }

    /**
     * Adds expanded wildcard information to this scope.
     */
    public void addExpandedWildcard(SqlIdentifier wildcard, List<SqlNode> expandedColumns) {
        if (wildcard != null && expandedColumns != null) {
            expandedWildcards.put(wildcard, new ArrayList<>(expandedColumns));
        }
    }

    /**
     * Adds a computed column alias to this scope.
     */
    public void addComputedColumn(String alias, ComputedColumnInfo computedColumnInfo) {
        if (alias != null && computedColumnInfo != null) {
            computedColumnMap.put(alias.toLowerCase(), computedColumnInfo);
        }
    }

    /**
     * Gets expanded wildcard information from this scope.
     */
    public List<SqlNode> getExpandedWildcard(SqlIdentifier wildcard) {
        return wildcard != null ? expandedWildcards.get(wildcard) : null;
    }

    /**
     * Checks if a wildcard has been expanded in this scope.
     */
    public boolean hasExpandedWildcard(SqlIdentifier wildcard) {
        return wildcard != null && expandedWildcards.containsKey(wildcard);
    }

    /**
     * Gets a table by its alias from this scope.
     */
    public SqlIdentifier getTableByAlias(String alias) {
        return alias != null ? aliasToTableMap.get(alias.toLowerCase()) : null;
    }

    /**
     * Gets a table by its name from this scope.
     */
    public SqlIdentifier getTableByName(String tableName) {
        return tableName != null ? tableNameMap.get(tableName.toLowerCase()) : null;
    }

    /**
     * Gets subquery information by alias from this scope.
     */
    public SubqueryInfo getSubqueryInfo(String alias) {
        return alias != null ? subqueryInfoMap.get(alias.toLowerCase()) : null;
    }

    /**
     * Checks if a CTE exists in this scope.
     */
    public boolean hasCTE(String name) {
        return name != null && cteDefinitions.containsKey(name.toLowerCase());
    }

    /**
     * Checks if a subquery exists in this scope.
     */
    public boolean hasSubquery(String alias) {
        return alias != null && subqueryInfoMap.containsKey(alias.toLowerCase());
    }

    /**
     * Gets all available subqueries in this scope.
     */
    public Map<String, SubqueryInfo> getAvailableSubqueries() {
        return new HashMap<>(subqueryInfoMap);
    }

    /**
     * Gets computed column information by alias from this scope.
     */
    public ComputedColumnInfo getComputedColumn(String alias) {
        return alias != null ? computedColumnMap.get(alias.toLowerCase()) : null;
    }

    /**
     * Checks if a computed column exists in this scope.
     */
    public boolean hasComputedColumn(String alias) {
        return alias != null && computedColumnMap.containsKey(alias.toLowerCase());
    }

    public void setSelectListItems(List<SqlNode> items) {
        this.selectListItems = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    public List<SqlNode> getSelectListItems() {
        return new ArrayList<>(selectListItems);
    }

    public SqlNode getSelectListItem(int ordinal) {
        if (ordinal >= 0 && ordinal < selectListItems.size()) {
            return selectListItems.get(ordinal);
        }
        return null;
    }

    /**
     * Gets all available tables in this scope.
     */
    public Set<SqlIdentifier> getAvailableTables() {
        return new LinkedHashSet<>(availableTables);
    }

    /**
     * Creates a deep copy of this scope.
     */
    public QueryScope copy() {
        QueryScope copy = new QueryScope();
        copy.aliasToTableMap.putAll(this.aliasToTableMap);
        copy.tableNameMap.putAll(this.tableNameMap);
        copy.availableTables.addAll(this.availableTables);
        copy.cteDefinitions.putAll(this.cteDefinitions);
        copy.subqueryInfoMap.putAll(this.subqueryInfoMap);
        copy.expandedWildcards.putAll(this.expandedWildcards);
        copy.computedColumnMap.putAll(this.computedColumnMap);
        copy.nestedOwnerMap.putAll(this.nestedOwnerMap);
        copy.nestedBaseTokenMap.putAll(this.nestedBaseTokenMap);
        copy.selectListItems = new ArrayList<>(this.selectListItems);
        return copy;
    }

    public void addNestedBinding(String alias, com.calcite_new.core.model.entity.DatabaseEntity owner, String baseToken) {
        if (alias == null || owner == null || baseToken == null) return;
        nestedOwnerMap.put(alias.toLowerCase(), owner);
        nestedBaseTokenMap.put(alias.toLowerCase(), baseToken);
    }

    public com.calcite_new.core.model.entity.DatabaseEntity getNestedOwner(String alias) {
        return alias != null ? nestedOwnerMap.get(alias.toLowerCase()) : null;
    }

    public String getNestedBaseToken(String alias) {
        return alias != null ? nestedBaseTokenMap.get(alias.toLowerCase()) : null;
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




