package com.calcite_new.sql.core.converter;

import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.util.SqlBasicVisitor;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class AggregateChecker {
// Add other necessary imports from your project structure if needed

  /**
   * Determines if a SqlSelect statement represents an aggregation query.
   *
   * <p>An aggregation query is defined as one that either:</p>
   * <ul>
   *     <li>Contains a {@code GROUP BY} clause.</li>
   *     <li>Contains a {@code HAVING} clause.</li>
   *     <li>Contains one or more aggregate functions (e.g., {@code SUM, COUNT, AVG})
   *         in the {@code SELECT} list, provided they are *not* exclusively used
   *         within an {@code OVER} clause (window functions).</li>
   * </ul>
   *
   * <p>This method performs the check by inspecting the structure of the
   * {@link SqlSelect} node directly, without relying on {@code SqlValidator}
   * annotations or state.</p>
   *
   * <p><b>Corner Cases Considered:</b></p>
   * <ul>
   *     <li>Explicit {@code GROUP BY}.</li>
   *     <li>Presence of {@code HAVING} (implies aggregation even if syntactically invalid
   *         in some contexts without {@code GROUP BY}).</li>
   *     <li>Aggregate functions in the {@code SELECT} list (e.g., {@code COUNT(*)}, {@code SUM(col)}).</li>
   *     <li>Global aggregation (aggregate functions in {@code SELECT} but no {@code GROUP BY}).</li>
   *     <li>Aggregate functions used inside window functions (e.g., {@code SUM(col) OVER (...)}) -
   *         these do *not* make the query itself an aggregate query in the {@code GROUP BY} sense.</li>
   *     <li>Aggregate functions nested inside other expressions (e.g., {@code MAX(a+b)},
   *         {@code CASE WHEN x THEN COUNT(y)...}).</li>
   *     <li>Aliases in the {@code SELECT} list (e.g., {@code SELECT COUNT(*) AS total}).</li>
   *     <li>Subqueries in the {@code SELECT} list (e.g., {@code SELECT (SELECT MAX(x) FROM t2) FROM t1}) -
   *         aggregates within such subqueries do *not* make the outer query aggregate.</li>
   *     <li>Empty or null {@code GROUP BY}, {@code HAVING}, or {@code SELECT} list.</li>
   * </ul>
   *
   * @param select The non-null {@link SqlSelect} node to analyze.
   * @return {@code true} if the select statement involves aggregation according to the rules above,
   *         {@code false} otherwise.
   */
  public static boolean isAggregate(SqlSelect select) {
    Objects.requireNonNull(select, "Input SqlSelect node cannot be null");

    // 1. Check for explicit GROUP BY clause.
    // An empty GROUP BY list (GROUP BY ()) is possible in some dialects but still indicates aggregation.
    if (select.getGroup() != null) {
      // Note: select.getGroup().size() > 0 might be technically more precise if GROUP BY ()
      // isn't considered aggregation, but generally, its presence implies aggregation intent.
      // Let's consider any non-null group list as aggregation.
      return true;
    }

    // 2. Check for HAVING clause.
    // The presence of a HAVING clause fundamentally requires aggregation,
    // even if the SQL might be semantically invalid without a GROUP BY or aggregates in SELECT.
    if (select.getHaving() != null) {
      return true;
    }

    // 3. Check for aggregate functions in the SELECT list (excluding those within OVER).
    SqlNodeList selectList = select.getSelectList();
    if (selectList != null) {
      // Use a dedicated visitor to find aggregate functions, correctly handling OVER clauses
      // and avoiding descent into subqueries found within the select list.
      SelectListItemAggregateFinder finder = new SelectListItemAggregateFinder();
      for (SqlNode selectItem : selectList) {
        if (selectItem == null) {
          continue; // Should not happen in a valid parse tree, but handle defensively.
        }

        // Handle potential alias (AS operator). We need to inspect the expression being aliased.
        SqlNode expressionToCheck = selectItem;
        if (selectItem.getKind() == SqlKind.AS && selectItem instanceof SqlCall) {
          SqlCall asCall = (SqlCall) selectItem;
          // Ensure operand list is not empty before accessing index 0
          if (!asCall.getOperandList().isEmpty()) {
            expressionToCheck = asCall.getOperandList().get(0);
          } else {
            // Invalid AS structure, treat as non-aggregate for safety
            expressionToCheck = null;
          }
        }

        // Apply the visitor to the core expression node.
        if (expressionToCheck != null) {
          expressionToCheck.accept(finder);
          if (finder.hasFoundAggregate()) {
            // Early exit: Found a relevant aggregate function.
            return true;
          }
          // Reset finder for the next select item if needed (though this finder stops on first hit)
          // finder.reset(); // Not strictly necessary with the current finder logic
        }
      }
    }

    // 4. No GROUP BY, no HAVING, and no relevant aggregate functions found in SELECT list.
    return false;
  }

  /**
   * A specialized visitor to detect the presence of aggregate functions within the
   * expression tree of a single SELECT list item.
   *
   * <p>Key behaviors:</p>
   * <ul>
   *     <li>Identifies calls to operators that are instances of {@link SqlAggFunction}.</li>
   *     <li>Explicitly stops traversal down the branch of an {@code OVER} clause, as aggregates
   *         within window functions do not count towards query-level aggregation.</li>
   *     <li>Explicitly stops traversal down into subqueries ({@code SELECT, UNION, VALUES,} etc.)
   *         encountered as expressions, as their internal aggregates are irrelevant here.</li>
   *     <li>Stops the entire visit process as soon as the first relevant aggregate function is found.</li>
   * </ul>
   */
  private static class SelectListItemAggregateFinder extends SqlBasicVisitor<Void> {
    private boolean foundAggregate = false;
    // Use a Set to handle potential cycles or redundant visits in complex expression trees,
    // although cycles are unlikely in valid SQL expressions.
    private final Set<SqlNode> visitedNodes = new HashSet<>();

    /**
     * Checks if a relevant aggregate function was found during the visit.
     * @return {@code true} if an aggregate was found, {@code false} otherwise.
     */
    public boolean hasFoundAggregate() {
      return foundAggregate;
    }

    // Optional: Reset state if the visitor instance were reused across multiple select items.
    // public void reset() {
    //     foundAggregate = false;
    //     visitedNodes.clear();
    // }

    @Override
    public Void visit(SqlCall call) {
      // --- Pre-visit checks for early exit or skipping ---

      // 1. Stop if already found.
      if (foundAggregate) {
        return null;
      }

      // 2. Avoid redundant visits/cycles.
      if (!visitedNodes.add(call)) {
        return null;
      }

      SqlOperator operator = call.getOperator();

      // --- Core Logic: Identify aggregates or nodes to skip ---

      // 3. Found an aggregate function? Mark and stop.
      if (operator instanceof SqlAggFunction) {
        // Check it's not part of an OVER clause (handled by OVER check below,
        // but this direct check is correct).
        foundAggregate = true;
        return null; // Stop visiting this branch and signal success.
      }

      // 4. Is it an OVER clause? Stop traversal down this specific path.
      // We do *not* want to find aggregates within the OVER(...) part.
      if (operator.equals(SqlStdOperatorTable.OVER)) {
        // Do not visit operands of OVER. Stop here for this branch.
        return null;
      }

      // 5. Is it a subquery used as an expression? Stop traversal down this path.
      // Aggregates inside subqueries don't make the *outer* query aggregate.
      SqlKind kind = call.getKind();
      if (kind == SqlKind.SELECT || kind == SqlKind.UNION || kind == SqlKind.INTERSECT ||
          kind == SqlKind.EXCEPT || kind == SqlKind.VALUES || kind == SqlKind.WITH ||
          kind == SqlKind.ORDER_BY || kind == SqlKind.SCALAR_QUERY || kind == SqlKind.EXISTS ||
          kind == SqlKind.CURSOR) // CURSOR might wrap a query
      {
        // Do not visit operands of the subquery structure. Stop here for this branch.
        return null;
      }

      // --- Default Behavior: Recurse into operands ---

      // 6. If none of the above, it's a regular function/operator. Visit operands
      //    to find potential nested aggregates.
      for (SqlNode operand : call.getOperandList()) {
        if (operand != null) {
          operand.accept(this);
          // Check if found after visiting an operand for early exit.
          if (foundAggregate) {
            return null;
          }
        }
      }
      return null;
    }

    @Override
    public Void visit(SqlNodeList nodeList) {
      if (foundAggregate || !visitedNodes.add(nodeList)) {
        return null;
      }
      for (SqlNode node : nodeList) {
        if (node != null) {
          node.accept(this);
          if (foundAggregate) {
            return null;
          }
        }
      }
      return null;
    }

    // Overriding visit for other types (SqlLiteral, SqlIdentifier, etc.) is usually
    // not necessary as they cannot contain aggregate calls themselves. The default
    // SqlBasicVisitor behavior for these is typically a no-op or returns null,
    // which is acceptable here. Add overrides if specific handling is needed.
    // For example:
    // @Override public Void visit(SqlIdentifier id) { visitedNodes.add(id); return null; }
    // @Override public Void visit(SqlLiteral literal) { visitedNodes.add(literal); return null; }
  }
}
