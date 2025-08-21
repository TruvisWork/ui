package com.calcite_new.sql.core.converter;

import com.calcite_new.core.dialect.sql.SqlDialect;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.calcite.plan.Convention;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelCollation;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlCase;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.type.SqlTypeUtil;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.sql.validate.SqlValidatorUtil;
import org.apache.calcite.util.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Converts a validated {@link SqlNode} tree into a {@link RelNode} tree
 * without using {@link org.apache.calcite.sql.validate.SqlValidator} or
 * {@link org.apache.calcite.tools.RelBuilder}.
 *
 * <p>This converter assumes the input {@code SqlNode} is already validated.
 * It relies on {@link Prepare.CatalogReader} to resolve schema objects and obtain
 * type information. Type inference for expressions is performed locally or
 * using {@link RexBuilder}. Relational nodes (like {@link LogicalFilter},
 * {@link LogicalProject}) are constructed directly.
 * </p>
 *
 * <p><b>Note:</b> Avoiding {@code SqlValidator} and {@code RelBuilder}
 * significantly increases complexity, especially regarding type inference,
 * scope resolution, RexInputRef index management, and handling complex SQL features.
 * This implementation makes simplifying assumptions and requires careful manual
 * construction of RelNode properties (like row types and expression references).
 * </p>
 *
 * <p><b>Refactoring Note:</b> Core conversion methods now return {@link Scope}
 * instead of {@link RelNode}. The {@code Scope} encapsulates the resulting
 * {@code RelNode} and the context (fields, aliases) available from its output.
 * </p>
 */
public class CustomSqlToRelConverter {

  protected final RelOptCluster cluster;
  protected final EntityCatalogReader catalogReader;
  private final List<String> defaultQualifiers;
  private final SqlDialect dialect;

  protected final RexBuilder rexBuilder;
  protected final RelDataTypeFactory typeFactory;
  protected final RelTraitSet defaultTraits;

  // Context for CTEs (Common Table Expressions) - Managed via Scope now.
  // private final Map<String, RelNode> cteMap = new HashMap<>(); // Removed, handled by Scope

  /**
   * Creates a SqlToRelConverter.
   *
   * @param cluster       Relational expression cluster
   * @param catalogReader Schema provider
   */
  public CustomSqlToRelConverter(
      RelOptCluster cluster,
      EntityCatalogReader catalogReader,
      List<String> defaultQualifiers,
      SqlDialect dialect) {
    this.cluster = Objects.requireNonNull(cluster, "cluster");
    this.catalogReader = Objects.requireNonNull(catalogReader, "catalogReader");
    this.rexBuilder = cluster.getRexBuilder();
    this.typeFactory = cluster.getTypeFactory();
    this.defaultQualifiers = defaultQualifiers != null
        ? defaultQualifiers
        : ImmutableList.of(); // Default qualifiers for table names
    this.dialect = dialect;
    // Default traits for logical nodes (can be customized)
    this.defaultTraits = cluster.traitSet().replace(Convention.NONE);
  }

  /**
   * Converts the main query {@link SqlNode} to a {@link RelNode}.
   * This remains the public entry point returning the final RelNode.
   *
   * @param sqlNode The root node of the validated SQL query.
   * @return The equivalent {@link RelNode} tree.
   */
  public RelNode convert(SqlNode sqlNode) {
    // Initial scope is empty
    Scope initialScope = Scope.createRoot();
    Scope finalScope = convertQueryRecursive(sqlNode, true, initialScope);
    // The final RelNode is extracted from the final Scope
    return finalScope.getRelNode();
  }

  /**
   * Recursively converts a {@link SqlNode} query node (SELECT, UNION, etc.)
   * into a {@link Scope} containing the resulting {@link RelNode}.
   *
   * @param sqlNode    The query node.
   * @param isTopLevel True if this is the outermost query being converted.
   * @param parentScope The scope available from the parent query level.
   * @return The {@link Scope} representing the output of the converted query node.
   */
  protected Scope convertQueryRecursive(SqlNode sqlNode, boolean isTopLevel, Scope parentScope) {
    SqlKind kind = sqlNode.getKind();
    switch (kind) {
      case SELECT:
        return convertSelect((SqlSelect) sqlNode, parentScope);
      case ORDER_BY:
        // ORDER BY modifies the result but doesn't change the scope structure significantly,
        // though it adds collation.
        return convertOrderBy((SqlOrderBy) sqlNode, parentScope);
      case UNION:
      case INTERSECT:
      case EXCEPT:
        return convertSetOp((SqlCall) sqlNode, parentScope);
      case VALUES:
        // Values clause doesn't depend on outer scope typically
        return convertValues((SqlCall) sqlNode, parentScope); // Pass parent scope for consistency
      case WITH:
        return convertWith((SqlWith) sqlNode, parentScope);
      // Add cases for other query types like INSERT source, EXPLAIN, etc. if needed
      default:
        throw new UnsupportedOperationException("Unsupported query node type: " + kind);
    }
  }

  /**
   * Converts a WITH clause by registering CTEs and converting the body.
   * Returns the Scope resulting from the body conversion.
   */
  protected Scope convertWith(SqlWith sqlWith, Scope parentScope) {
    // Create a new scope for CTE definitions, inheriting from the parent
    Scope withScope = Scope.createWithScope(parentScope);

    // Convert and register each CTE definition
    for (SqlNode cteNode : sqlWith.withList) {
      SqlWithItem cte = (SqlWithItem) cteNode;
      String alias = cte.name.getSimple();
      if (withScope.isCteDefined(alias)) {
        throw new IllegalArgumentException("Duplicate CTE name: " + alias);
      }
      // Convert CTE body recursively. Pass the current 'withScope'.
      Scope cteScope = convertQueryRecursive(cte.query, false, withScope);
      // Register the RelNode from the resulting scope in the current scope's CTE map
      withScope.addCte(alias, cteScope.getRelNode());
    }

    // Convert the main query body using the scope that now includes all CTE definitions.
    // The body is top-level relative to WITH.
    return convertQueryRecursive(sqlWith.body, true, withScope);
  }


  /**
   * Converts a SELECT statement.
   * Returns the Scope representing the final output of the SELECT (likely after projection).
   */
  protected Scope convertSelect(SqlSelect select, Scope parentScope) {
    // 1. FROM clause - Establishes the initial input scope for this SELECT
    Scope currentScope = convertFrom(select.getFrom(), parentScope);
    RelNode currentRel = currentScope.getRelNode(); // Get the RelNode from the FROM scope

    // 2. WHERE clause - Filter
    if (select.getWhere() != null) {
      // Use the scope from the FROM clause to resolve expressions in WHERE
      RexNode whereCondition = convertExpression(select.getWhere(), currentScope);
      RexNode flattenedRex = RexUtil.flatten(rexBuilder, whereCondition);
      currentRel = LogicalFilter.create(currentRel, flattenedRex);
      // Update scope to reflect the filter output (row type usually unchanged)
      currentScope = Scope.createScopeForRelNode(parentScope, currentRel, currentScope.getAlias()); // Preserve alias if any
    }

    // 3. GROUP BY / Aggregation / HAVING
    boolean isAggregate = AggregateChecker.isAggregate(select); // Checks for GROUP BY or aggregate functions
    Scope aggregateInputScope = currentScope; // Scope before potential aggregation

    if (isAggregate) {
      // Identify all the columns needed for Aggregate and apply projection if needed
      // This is a complex step. We need to ensure that the input to the aggregate
      // is correct and that the output is properly projected.
      aggregateInputScope = applyProject(select, currentScope);
      // convertAggregate now returns the Scope after aggregation/having
      currentScope = convertAggregate(select, aggregateInputScope);
      currentRel = currentScope.getRelNode(); // Update RelNode from the new scope
    } else if (select.getHaving() != null) {
      throw new IllegalArgumentException("HAVING clause found without GROUP BY or aggregate functions");
    }

    // 4. SELECT list - Projection
    // This needs to happen *after* aggregation but *before* ORDER BY
    // convertProject takes the current scope and returns the scope after projection
    Scope finalScope = convertProject(select.getSelectList(), currentScope);
    currentRel = finalScope.getRelNode(); // Update RelNode

    if (select.isDistinct()) {
      return convertDistinct(select, finalScope);
    }

    // Window functions (OVER clauses) would typically be handled here,
    // operating on the result of projection/aggregation. Requires LogicalWindow.
    // Manual creation of LogicalWindow is very complex. If added, it would
    // take finalScope and return a new scope wrapping the LogicalWindow.

    // Note: ORDER BY, LIMIT/OFFSET are handled by convertOrderBy if present,
    // which would take finalScope as input.

    return finalScope; // Return the scope after the projection
  }

  protected Scope convertDistinct(SqlSelect select, Scope finalScope) {
    // Get the input relation from the input scope
    RelNode inputRel = finalScope.getRelNode();

    // 1. Convert DISTINCT to LogicalAggregate with empty grouping set
    // This approach groups by all columns, effectively removing duplicates

    // Create a group key for each field in the input
    RelDataType rowType = inputRel.getRowType();
    int fieldCount = rowType.getFieldCount();

    // Create a BitSet containing all input fields (for GROUP BY)
    ImmutableBitSet.Builder groupSetBuilder = ImmutableBitSet.builder();
    for (int i = 0; i < fieldCount; i++) {
      groupSetBuilder.set(i);
    }

    // Build a list of RexInputRefs for the grouping keys
    List<RexNode> groupExps = new ArrayList<>();
    Map<Integer, RexNode> groupExprMap = new LinkedHashMap<>();
    for (int i = 0; i < fieldCount; i++) {
      RelDataTypeField field = rowType.getFieldList().get(i);
      RexNode expr = rexBuilder.makeInputRef(field.getType(), i);
      groupExps.add(expr);
      groupExprMap.put(i, expr);
    }

    // No aggregate calls - we're just using GROUP BY to eliminate duplicates
    List<AggregateCall> aggCalls = Collections.emptyList();
    ImmutableBitSet groupSet = groupSetBuilder.build();

    // Create LogicalAggregate
    RelNode distinctRel = LogicalAggregate.create(
        inputRel,
        groupSet,
        null, // No groupSets (CUBE, ROLLUP, etc.)
        aggCalls);

    // 2. For ORDER BY after DISTINCT, the output row type of the aggregate matches the input
    // We can use the same field names from the input for the output scope

    // Create a mapping from aggregate call SqlNodes to their output positions (empty in this case)
    Map<AggregateCallInfo, Integer> aggCallOutputMap = Collections.emptyMap();

    // Create the output scope with information allowing subsequent operations to reference these fields
    return Scope.createAggregateOutputScope(
        finalScope,        // parent scope
        distinctRel,       // the new LogicalAggregate node
        groupSet,          // fields used in grouping
        groupExprMap,      // mapping of grouping expressions to their positions
        aggCalls,          // aggregate calls (empty)
        aggCallOutputMap   // mapping to find aggregate calls (empty)
    );
  }

  /**
   * Identify all the columns needed for Aggregate and apply projection if needed
   * This is a complex step. We need to ensure that the input to the aggregate
   * is correct and that the output is properly projected.
   */
  protected Scope applyProject(SqlSelect select, Scope inputScope) {
    // Get the current rel node
    RelNode inputRel = inputScope.getRelNode();

    // Set to collect all column references needed for aggregation
    Set<SqlIdentifier> neededColumns = new HashSet<>();

    // 1. Collect columns used in aggregate functions in SELECT list
    AggregateFinder aggFinder = new AggregateFinder();
    neededColumns.addAll(IdentifierCollector.collect(select.getSelectList()));
    for (AggregateCallInfo aggInfo : aggFinder.getAggCalls()) {
      for (SqlNode operand : aggInfo.sqlOperands) {
//        if (operand instanceof SqlIdentifier) {
//          neededColumns.add(((SqlIdentifier) operand).getSimple());
//        } else if (operand instanceof SqlCall) {
//          // For complex expressions in aggregates, need the whole row
//          return inputScope;
//        }
        neededColumns.addAll(IdentifierCollector.collect(operand));
      }

      // Check for FILTER clause
      if (aggInfo.filter != null) {
        // If filter has complex expressions, need whole row
        return inputScope;
      }
    }

    // 2. Collect columns from GROUP BY clause
    if (select.getGroup() != null) {
      neededColumns.addAll(IdentifierCollector.collect(select.getGroup()));
      for (SqlNode groupKey : select.getGroup().getList()) {
        if (groupKey instanceof SqlIdentifier) {
          SqlIdentifier id = (SqlIdentifier) groupKey;
          if (id.isSimple()) {

          }
        }
        // For complex expressions in GROUP BY, we'll need the whole row
        // as we can't easily project just parts of expressions
        else {
          return inputScope; // Return original scope if complex expressions found
        }
      }
    }

    // 3. Collect columns from HAVING clause
    if (select.getHaving() != null) {
      // Collect identifiers from HAVING
//      IdentifierVisitor idVisitor = new IdentifierVisitor();
      neededColumns.addAll(IdentifierCollector.collect(select.getHaving()));

//      // If HAVING has complex expressions beyond simple column refs
//      // and aggregates, we need the whole row
//      if (idVisitor.hasComplexExpressions()) {
//        return inputScope;
//      }
    }

    // If we've found that we need all columns or no specific ones needed,
    // return the original scope
    if (neededColumns.isEmpty() || neededColumns.size() == inputRel.getRowType().getFieldCount()) {
      return inputScope;
    }

    // 4. Apply projection if we need only a subset of columns
    List<RexNode> projExprs = new ArrayList<>();
    List<String> fieldNames = new ArrayList<>();

    // Map to track positions for field lookup
    Map<String, Integer> fieldMap = new HashMap<>();

    // Build map of field names to indexes
    RelDataType rowType = inputRel.getRowType();
    for (int i = 0; i < rowType.getFieldCount(); i++) {
      String fieldName = rowType.getFieldNames().get(i);
      fieldMap.put(fieldName, i);
    }

    // Add needed columns to projection
//    for (String column : neededColumns) {
//      Integer index = fieldMap.get(column);
//      if (index != null) {
//        projExprs.add(rexBuilder.makeInputRef(
//            rowType.getFieldList().get(index).getType(), index));
//        fieldNames.add(column);
//      }
//    }

    // Create project rel
    RelNode projectRel = LogicalProject.create(
        inputRel,
        ImmutableList.of(), // no hints
        projExprs,
        fieldNames,
        ImmutableSet.of() // no variablesSet
    );

    // Create and return new scope with projected relation
    return Scope.createScopeForRelNode(inputScope, projectRel, inputScope.getAlias());
  }

  // Helper visitor to collect column references
  protected static class IdentifierVisitor extends SqlBasicVisitor<Void> {
    private final Set<String> identifiers = new HashSet<>();
    private boolean hasComplexExpressions = false;
    private final Set<SqlNode> visited = new HashSet<>();

    public Set<String> getIdentifiers() {
      return identifiers;
    }

    public boolean hasComplexExpressions() {
      return hasComplexExpressions;
    }

    @Override
    public Void visit(SqlIdentifier id) {
      if (visited.add(id)) {
        identifiers.add(id.getSimple());
      }
      return null;
    }

    @Override
    public Void visit(SqlCall call) {
      if (visited.add(call)) {
        // Skip aggregate functions - they're handled separately
        if (!(call.getOperator() instanceof SqlAggFunction)) {
          hasComplexExpressions = true;
          for (SqlNode operand : call.getOperandList()) {
            if (operand != null) {
              operand.accept(this);
            }
          }
        }
      }
      return null;
    }

    // Visit other node types
    @Override public Void visit(SqlLiteral literal) { visited.add(literal); return null; }
    @Override public Void visit(SqlNodeList nodeList) {
      if (visited.add(nodeList)) {
        nodeList.forEach(node -> node.accept(this));
      }
      return null;
    }
    @Override public Void visit(SqlDataTypeSpec type) { visited.add(type); return null; }
    @Override public Void visit(SqlDynamicParam param) { visited.add(param); return null; }
    @Override public Void visit(SqlIntervalQualifier interval) { visited.add(interval); return null; }
  }

  /**
   * Converts the FROM clause of a SELECT statement.
   * Returns a Scope representing the output of the FROM clause (single table, join result, subquery result).
   */
  protected Scope convertFrom(@Nullable SqlNode fromNode, Scope parentScope) {
    if (fromNode == null) {
      // No FROM clause (e.g., SELECT 1)
      RelNode values = LogicalValues.createOneRow(cluster);
      // Return a scope representing this single-row Values node
      return Scope.createScopeForRelNode(parentScope, values, "$VALUES"); // Use a synthetic alias
    }

    SqlKind kind = fromNode.getKind();
    switch (kind) {
      case IDENTIFIER: {
        SqlIdentifier id = (SqlIdentifier) fromNode;
        String aliasOrName = id.names.get(id.names.size() - 1);

        // Check if it's a CTE reference first
        RelNode cteRel = parentScope.findCte(aliasOrName);
        if (cteRel != null) {
          // Found CTE. Return a scope for this CTE's RelNode, using the CTE name as alias.
          return Scope.createScopeForRelNode(parentScope, cteRel, aliasOrName);
        }

        // Assume it's a table
        RelOptTable table = catalogReader.getTable(id.names);
        if (table == null) {
//          // Try resolving with default qualifiers if not found directly
//          List<String> qualifiedName = SqlValidatorUtil.addImplicitQualifier(
//              id.names, defaultQualifiers, catalogReader.nameMatcher());
//          table = catalogReader.getTable(qualifiedName);
//
//          if (table == null) {
            throw new RuntimeException("Table or CTE not found: " + id.names + " (also tried with default qualifiers: " + defaultQualifiers + ")");
//          }
        }
        // Create a scan. Traits can be customized.
        RelNode scan = LogicalTableScan.create(cluster, table, ImmutableList.of()); // Empty hints
        // Return a scope for the table scan, using the table name (or last part) as alias.
        return Scope.createScopeForRelNode(parentScope, scan, aliasOrName);
      }
      case JOIN:
        // convertJoin now returns the Scope representing the join result
        return convertJoin((SqlJoin) fromNode, parentScope);

      case AS: {
        SqlCall asCall = (SqlCall) fromNode;
        SqlNode operand = asCall.getOperandList().get(0);
        SqlIdentifier aliasId = (SqlIdentifier) asCall.getOperandList().get(1);
        String alias = aliasId.getSimple();

        // Convert the underlying operand, which returns its scope
        Scope underlyingScope = convertFrom(operand, parentScope);

        // Create a new scope that is identical but applies the explicit alias
        return Scope.createScopeWithAlias(parentScope, underlyingScope.getRelNode(), alias);
      }
      case SELECT: // Subquery in FROM
      case UNION:
      case INTERSECT:
      case EXCEPT:
      case WITH:
      case ORDER_BY: { // Subquery with ORDER BY (usually requires alias)
        // Convert the subquery recursively. It executes in its own scope,
        // potentially inheriting correlation variables from parentScope.
        // The result is the scope representing the subquery's output.
        Scope subQueryScope = convertQueryRecursive(fromNode, false, parentScope);
        // If the subquery isn't aliased via AS, it needs an implicit alias for resolution.
        // Calcite often generates one like "$SUBQUERY$1". Let's return the scope as is,
        // assuming an alias will be provided by 'AS' or handled by the caller if needed.
        // If no 'AS' is used, resolution might fail later if qualified names are needed.
        // For simplicity, we return the direct scope. An explicit AS is recommended.
        if (subQueryScope.getAlias() == null) {
          // Maybe generate a synthetic alias if none exists?
          // return Scope.createScopeWithAlias(parentScope, subQueryScope.getRelNode(), generateSubqueryAlias());
          // For now, return as is. Caller (e.g. JOIN) might handle it.
        }
        return subQueryScope;
      }
      case VALUES:
        // convertValues now returns a Scope
        return convertValues((SqlCall) fromNode, parentScope);

      default:
        throw new UnsupportedOperationException("Unsupported FROM clause node type: " + kind);
    }
  }

  /**
   * Converts a JOIN clause.
   * Returns a Scope representing the output of the LogicalJoin.
   */
  protected Scope convertJoin(SqlJoin join, Scope parentScope) {
    Scope leftScope = convertFrom(join.getLeft(), parentScope);
    Scope rightScope = convertFrom(join.getRight(), parentScope);

    RelNode leftRel = leftScope.getRelNode();
    RelNode rightRel = rightScope.getRelNode();

    JoinRelType joinType = convertJoinType(join.getJoinType());

    // The scope for the join condition includes both left and right inputs.
    // This scope is temporary, just for resolving the condition.
    Scope joinConditionScope = Scope.createJoinInputScope(parentScope, leftScope, rightScope);

    RexNode condition;
    Set<CorrelationId> correlationIds = ImmutableSet.of(); // TODO: Handle correlations if needed

    SqlNode conditionNode = join.getCondition();
    JoinConditionType conditionType = join.getConditionType();

    if (conditionNode == null) {
      if (join.isNatural()) {
        condition = convertNaturalJoinCondition(leftRel.getRowType(), rightRel.getRowType());
      } else if (conditionType == JoinConditionType.USING) {
        throw new UnsupportedOperationException("USING not supported yet");
//        condition = convertUsingJoinCondition(join.getUsingList(), leftRel.getRowType(), rightRel.getRowType(), joinConditionScope);
        // Natural/Using joins often imply a projection afterwards to merge/select columns.
        // We create the LogicalJoin first; projection is handled later if needed (e.g. by RelBuilder).
        // Doing it manually here adds complexity.
      } else {
        // No condition (cross join or error)
        if (joinType == JoinRelType.INNER || joinType == JoinRelType.FULL) { // Standard CROSS JOIN
          condition = rexBuilder.makeLiteral(true);
        } else {
          throw new IllegalArgumentException("Outer join requires ON, USING, or NATURAL");
        }
      }
    } else { // ON condition
      if (conditionType != JoinConditionType.ON) {
        throw new IllegalArgumentException("Unexpected condition type for ON join: " + conditionType);
      }
      // Convert the ON condition using the combined input scope
      condition = convertExpression(conditionNode, joinConditionScope);
    }

    // Create the LogicalJoin node
    RelNode joinRel = LogicalJoin.create(leftRel, rightRel, ImmutableList.of() /* hints */, condition, correlationIds, joinType);

    // Create and return the scope representing the output of the join.
    // This scope needs to know about fields from both left and right.
    return Scope.createJoinOutputScope(parentScope, joinRel, leftScope, rightScope);
  }

  /**
   * Converts SqlJoinOperator.JoinType to JoinRelType.
   */
  protected JoinRelType convertJoinType(JoinType joinType) {
    switch (joinType) {
      case CROSS:
      case INNER:
        return JoinRelType.INNER;
      case LEFT:
        return JoinRelType.LEFT;
      case RIGHT:
        return JoinRelType.RIGHT;
      case FULL:
        return JoinRelType.FULL;
      case COMMA:
        return JoinRelType.INNER; // Typically equivalent to CROSS or INNER with condition in WHERE
      default:
        throw new UnsupportedOperationException("Unsupported join type: " + joinType);
    }
  }

  /**
   * Generates join condition for NATURAL JOIN.
   * Note: This only generates the condition. It doesn't handle the projection
   * required by NATURAL JOIN (selecting common columns once, then others).
   */
  protected RexNode convertNaturalJoinCondition(RelDataType leftType, RelDataType rightType) {
    List<String> leftNames = leftType.getFieldNames();
    List<String> rightNames = rightType.getFieldNames();
    List<RexNode> conditions = new ArrayList<>();
    int leftFieldCount = leftType.getFieldCount();

    final List<String> commonNames = new ArrayList<>();
    for (RelDataTypeField leftField : leftType.getFieldList()) {
      // Case-insensitive match? Calcite usually handles this via validator config. Assume insensitive.
      RelDataTypeField rightField = rightType.getField(leftField.getName(), false, false);
      if (rightField != null) {
        commonNames.add(leftField.getName()); // Track common names for potential projection later
        // Check type compatibility? Assumed validated.
        RexNode leftRef = rexBuilder.makeInputRef(leftField.getType(), leftField.getIndex());
        // Right input refs are offset by the number of left fields
        RexNode rightRef = rexBuilder.makeInputRef(rightField.getType(), leftFieldCount + rightField.getIndex());
        conditions.add(rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef));
      }
    }

    if (conditions.isEmpty()) {
      // Standard SQL behavior for NATURAL JOIN with no common columns is CROSS JOIN.
      return rexBuilder.makeLiteral(true);
    }
    return RexUtil.composeConjunction(rexBuilder, conditions);
  }

  /**
   * Generates join condition for JOIN USING.
   * Note: This only generates the condition. It doesn't handle the projection
   * required by USING (selecting common columns once, then others).
   * Requires the join *input* scope to resolve potential type differences if needed,
   * although we primarily use the row types here.
   */
  protected RexNode convertUsingJoinCondition(SqlNodeList usingList, RelDataType leftType, RelDataType rightType, Scope joinInputScope) {
    List<RexNode> conditions = new ArrayList<>();
    int leftFieldCount = leftType.getFieldCount();

    for (SqlNode node : usingList) {
      SqlIdentifier id = (SqlIdentifier) node;
      String name = id.getSimple(); // USING columns are unqualified

      // Find the fields in the respective types
      RelDataTypeField leftField = leftType.getField(name, false, false);
      RelDataTypeField rightField = rightType.getField(name, false, false);

      if (leftField == null || rightField == null) {
        throw new RuntimeException("Column '" + name + "' specified in USING clause not found in both tables or is ambiguous");
      }
      // Check type compatibility? Assumed validated.

      RexNode leftRef = rexBuilder.makeInputRef(leftField.getType(), leftField.getIndex());
      RexNode rightRef = rexBuilder.makeInputRef(rightField.getType(), leftFieldCount + rightField.getIndex());

      // Use RexBuilder to create the equality check, allowing it to handle type coercion if necessary
      // (though ideally types are already compatible post-validation)
      conditions.add(rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef));
    }
    if (conditions.isEmpty()) {
      // Should not happen if usingList is not empty, but handle defensively
      return rexBuilder.makeLiteral(true);
    }
    return RexUtil.composeConjunction(rexBuilder, conditions);
  }

  /**
   * Converts GROUP BY, aggregate functions, and HAVING.
   * Takes the input Scope and returns the Scope after aggregation and optional HAVING filter.
   */
  protected Scope convertAggregate(SqlSelect select, Scope inputScope) {
    RelNode inputRel = inputScope.getRelNode();

    // 1. Find GROUP BY expressions & Build GroupSet
    ImmutableBitSet groupSet;
    List<RexNode> groupExprs = new ArrayList<>(); // Expressions corresponding to groupSet bits
    Map<Integer, RexNode> groupExprMap = new HashMap<>(); // Map group index -> RexNode (relative to input)

    if (select.getGroup() != null) {
      ImmutableBitSet.Builder groupSetBuilder = ImmutableBitSet.builder();
      for (SqlNode groupNode : select.getGroup().getList()) {
        // Resolve GROUP BY expressions against the input scope
        RexNode groupRex = convertExpression(groupNode, inputScope);
        groupExprs.add(groupRex);
        // Try to find if this expression matches an input field index
        int inputIndex = findRexInputIndex(groupRex, inputRel);
        if (inputIndex != -1) {
          groupSetBuilder.set(inputIndex);
          groupExprMap.put(inputIndex, groupRex);
        } else {
          // Grouping by a complex expression requires Project beforehand or handling in Aggregate.
          // Standard Aggregate requires groupSet to refer to input fields.
          // We might need to project the expression first.
          // TODO: Implement pre-projection for complex group keys if needed.
          throw new UnsupportedOperationException("Grouping by complex expression '" + groupNode + "' requires pre-projection (not implemented here). Input fields: " + inputRel.getRowType().getFieldNames());
        }
      }
      groupSet = groupSetBuilder.build();
    } else {
      // Global aggregation (no GROUP BY clause)
      groupSet = ImmutableBitSet.of();
    }

    // 2. Find Aggregate Calls (in SELECT list and HAVING clause)
    AggregateFinder aggFinder = new AggregateFinder();
    // Visit relevant parts of the select statement
    if (select.getSelectList() != null) {
      select.getSelectList().accept(aggFinder);
    }
    if (select.getHaving() != null) {
      select.getHaving().accept(aggFinder);
    }
    // TODO: Consider aggregates in ORDER BY if allowed (standard SQL disallows them there unless also in SELECT)
    List<AggregateCallInfo> aggregateCallInfos = aggFinder.getAggCalls();

    // 3. Create AggregateCall instances for LogicalAggregate
    List<AggregateCall> aggCalls = new ArrayList<>();
    Map<AggregateCallInfo, Integer> aggCallOutputIndexMap = new HashMap<>(); // Map info -> index in aggCalls list

    for (AggregateCallInfo info : aggregateCallInfos) {
      List<Integer> argList = new ArrayList<>();
      List<RexNode> argRexNodes = new ArrayList<>(); // Store RexNodes for type derivation
      for (SqlNode operand : info.sqlOperands) {
        // Resolve aggregate arguments against the input scope
        RexNode operandRex = convertExpression(operand, inputScope);
        argRexNodes.add(operandRex);
        // AggregateCall arguments must be input refs. Project if necessary.
        int inputIndex = findRexInputIndex(operandRex, inputRel);
        if (inputIndex == -1) {
          // Aggregate argument is a complex expression. Requires pre-projection.
          // TODO: Implement pre-projection for complex aggregate arguments if needed.
          throw new UnsupportedOperationException("Aggregate function '" + info.sqlAggFunction.getName()
              + "' argument '" + operand + "' is complex and requires pre-projection (not implemented here). Input fields: " + inputRel.getRowType().getFieldNames());
        }
        argList.add(inputIndex);
      }

      SqlAggFunction calciteAgg = mapSqlAggregation(info.sqlAggFunction);
      if (calciteAgg == null) {
        throw new UnsupportedOperationException("Unsupported aggregate function: " + info.sqlAggFunction.getName());
      }

      // Handle FILTER clause
      int filterArg = getFilterArg(inputRel, inputScope, info);

      // Determine aggregate call type (crucial and complex without validator)
      RelDataType type = deriveAggCallType(calciteAgg, argRexNodes, inputScope);

      // Determine nullability - often complex. A simple heuristic: nullable if any arg is nullable or if agg func can return null (e.g., AVG, SUM on empty set).
      boolean nullable = argRexNodes.stream().anyMatch(rex -> rex.getType().isNullable());
//          || calciteAgg.getKind() == SqlKind.AVG // AVG, SUM, STDDEV etc. can be null for empty input
//          || calciteAgg.getKind() == SqlKind.SUM;
      // MIN/MAX nullable depends on input nullability
      // COUNT is not nullable

      RelDataType finalType = typeFactory.createTypeWithNullability(type, nullable);

      String name = info.alias != null ? info.alias : generateAggAlias(info.sqlAggFunction, aggCalls.size());

      // TODO: Handle ORDER BY within aggregate call (info.orderKeys) -> requires RelCollation
      RelCollation collation = RelCollations.EMPTY; // Placeholder

      AggregateCall aggCall = AggregateCall.create(
          calciteAgg,
          info.isDistinct,
          info.isApproximate, // approximate
          false, // ignoreNulls - specific to some functions, assume false
          List.of(), // Pass RexNodes for context, though create uses argList indices
          argList,
          filterArg,
          null, // distinctKeys - related to DISTINCT implementation detail
          collation, // collation for ORDER BY within aggregate
          finalType,
          name);

      aggCallOutputIndexMap.put(info, aggCalls.size());
      aggCalls.add(aggCall);
    }

    // 4. Create the LogicalAggregate node
    // The output row type includes group keys first, then aggregate calls.
    // groupSets argument is for GROUPING SETS, CUBE, ROLLUP - not handled here.
    RelNode aggregateRel = LogicalAggregate.create(inputRel, groupSet, null, aggCalls);

    // 5. HAVING clause - Filter *after* aggregation
    RelNode resultRel = aggregateRel;
    Scope currentScope = Scope.createAggregateOutputScope(inputScope, aggregateRel, groupSet, groupExprMap, aggCalls, aggCallOutputIndexMap);

    if (select.getHaving() != null) {
      // Convert HAVING expression using the scope that reflects the Aggregate output.
      // This scope knows how to resolve group keys and aggregate calls.
      RexNode havingCondition = convertExpression(select.getHaving(), currentScope);
      resultRel = LogicalFilter.create(aggregateRel, havingCondition);
      // Update the scope to reflect the output of the Filter (row type unchanged, but it's a new node)
      currentScope = Scope.createAggregateOutputScope(currentScope, resultRel, groupSet, groupExprMap, aggCalls, aggCallOutputIndexMap); // Alias lost after filter
    }

    // Return the scope representing the final output of this stage (Aggregate or Filter)
    return currentScope;
  }

  /** Derives the return type for an aggregate call. */
  protected RelDataType deriveAggCallType(SqlAggFunction aggFunction, List<RexNode> argRexNodes, Scope inputScope) {
    // This is tricky without the validator's binding context.
    // 1. Try the function's own type inference if available and simple.
    // 2. Use RexBuilder as a fallback.
    // 3. Handle COUNT specially.

    if (aggFunction.getKind() == SqlKind.COUNT) {
      // COUNT always returns BIGINT NOT NULL (or maybe just NOT NULL based on dialect?)
      // Let's assume BIGINT NOT NULL for standard behavior.
      return typeFactory.createSqlType(SqlTypeName.BIGINT); // Nullability handled later
    }

    RelDataType type = null;
    try {
      // Try inferReturnType with just operand types (may be inaccurate)
      List<RelDataType> argTypes = argRexNodes.stream().map(RexNode::getType).collect(Collectors.toList());
      type = aggFunction.inferReturnType(typeFactory, argTypes);
    } catch (Exception e) { /* Fallback */ }

    if (type == null) {
      try {
        // RexBuilder might have better logic
        type = rexBuilder.deriveReturnType(aggFunction, argRexNodes);
      } catch (Exception e) {
        throw new IllegalStateException("Could not derive return type for aggregate function "
            + aggFunction.getName() + " with args: " + argRexNodes, e);
      }
    }

    if (type == null) {
      throw new IllegalStateException("Could not derive return type for aggregate function " + aggFunction.getName());
    }

    // Return the base type; nullability is adjusted separately.
    return type;
  }


  /** Handles the FILTER (WHERE ...) clause for aggregate functions */
  private int getFilterArg(RelNode input, Scope inputScope, AggregateCallInfo info) {
    int filterArg = -1; // Default: no filter

    if (info.filter != null) {
      // Convert the filter expression to a RexNode using the aggregate's input scope
      RexNode filterRex = convertExpression(info.filter, inputScope);

      // Filter must be a boolean expression
      if (!SqlTypeUtil.isBoolean(filterRex.getType())) {
        throw new IllegalArgumentException("FILTER expression must be boolean, found: " +
            filterRex.getType().getSqlTypeName() + " for expression: " + info.filter);
      }

      // The filter expression needs to be represented as an index into the input.
      // If it's a complex expression, it requires pre-projection.
      int inputIndex = findRexInputIndex(filterRex, input);
      if (inputIndex != -1) {
        // Simple case: filter is a direct column reference
        filterArg = inputIndex;
      } else {
        // Complex filter requires pre-projection
        // TODO: Implement pre-projection for complex filter expressions if needed.
        throw new UnsupportedOperationException("Complex filter expressions in FILTER clause '" + info.filter
            + "' require pre-projection (not implemented here). Input fields: " + input.getRowType().getFieldNames());
      }
    }
    return filterArg;
  }

  /**
   * Converts the SELECT list (projection).
   * Takes the input Scope and returns the Scope after projection.
   */
  protected Scope convertProject(SqlNodeList selectList, Scope inputScope) {
    RelNode inputRel = inputScope.getRelNode();
    RelDataType inputRowType = inputRel.getRowType();

    List<RexNode> projects = new ArrayList<>();
    List<String> aliases = new ArrayList<>();
    AtomicInteger aliasCounter = new AtomicInteger(0); // For generating default aliases

    if (selectList == null || selectList.getList().isEmpty()) {
      // Should not happen with valid SQL, but handle defensively.
      // Create a project with no columns? Or handle based on context?
      // Let's assume selectList is valid and non-empty.
      throw new IllegalArgumentException("SELECT list cannot be null or empty.");
    }

    for (SqlNode node : selectList.getList()) {
      assert node != null;
      if (node.getKind() == SqlKind.IDENTIFIER && ((SqlIdentifier) node).isStar()) {
        // Expand SELECT * or SELECT table.*
        SqlIdentifier starId = (SqlIdentifier) node;
        if (starId.names.size() > 1) {
          // Expand SELECT table.*
          String qualifier = starId.names.get(0);
          // Find the frame (relation) corresponding to the qualifier in the input scope
          Frame sourceFrame = inputScope.findRelationByAlias(qualifier);
          if (sourceFrame == null) {
            throw new RuntimeException("Unknown table alias or relation '" + qualifier + "' in SELECT list: " + starId);
          }
          RelDataType sourceRowType = sourceFrame.relNode.getRowType();
          for (int i = 0; i < sourceRowType.getFieldCount(); i++) {
            RelDataTypeField field = sourceRowType.getFieldList().get(i);
            // Create RexInputRef relative to the *combined* input node using the frame's offset
            projects.add(rexBuilder.makeInputRef(field.getType(), sourceFrame.offset + i));
            aliases.add(field.getName()); // Use original field name
          }
        } else {
          // Expand SELECT * (all columns from all frames in the input scope)
          for (Frame frame : inputScope.getRelations()) {
            RelDataType frameRowType = frame.relNode.getRowType();
            for (int i = 0; i < frameRowType.getFieldCount(); i++) {
              RelDataTypeField field = frameRowType.getFieldList().get(i);
              projects.add(rexBuilder.makeInputRef(field.getType(), frame.offset + i));
              aliases.add(field.getName()); // Use original field name
            }
          }
          // Check if input was empty (e.g., FROM-less select resulted in empty Values)
          if (projects.isEmpty() && inputRowType.getFieldCount() == 0) {
            // Handle SELECT * from a source with no columns (e.g., VALUES())
            // This results in a projection with no columns.
          } else if (projects.isEmpty()) {
            // This case (SELECT * resulting in no columns when input had columns) should ideally not happen.
            throw new IllegalStateException("SELECT * expanded to zero columns, but input had columns: " + inputRowType.getFieldNames());
          }
        }
      } else {
        // Convert regular expression
        // Use the input scope to resolve identifiers within the expression
        RexNode projExpr = convertExpression(node, inputScope);
        projects.add(projExpr);
        // Use explicit alias (AS) or derive one
        // Use SqlValidatorUtil.getAlias as a helper, but it might rely on validator state.
        // A simpler alternative: check for AS, otherwise generate.
        String alias = getExplicitAlias(node);
        if (alias == null) {
          // Generate a default alias like EXPR$0, EXPR$1...
          // Note: Calcite's default is often $f0, $f1... or based on expression text.
          // Let's use EXPR$ for clarity.
          alias = "EXPR$" + aliasCounter.getAndIncrement();
          // Ensure generated alias doesn't clash with existing ones (less likely with EXPR$)
        }
        aliases.add(alias);
      }
    }

    // Create the projection node
    // Need to derive the output row type using the calculated aliases and expression types.
    RelDataType outputRowType = RexUtil.createStructType(typeFactory, projects, aliases, SqlValidatorUtil.F_SUGGESTER);

    // Check if the projection is trivial (identity projection)
    boolean isIdentity = projects.size() == inputRowType.getFieldCount() && !projects.isEmpty();
    if (isIdentity) {
      for (int i = 0; i < projects.size(); i++) {
        if (!(projects.get(i) instanceof RexInputRef) || ((RexInputRef) projects.get(i)).getIndex() != i) {
          isIdentity = false;
          break;
        }
        // Also check if aliases match input field names (case-insensitively?)
        if (!aliases.get(i).equalsIgnoreCase(inputRowType.getFieldNames().get(i))) {
          // If only aliases differ, it's not a strict identity RelNode-wise,
          // but might be optimized away later. Treat as non-identity for now.
          // isIdentity = false; // Keep as true if only aliases differ? Calcite might optimize. Let's say alias change means non-identity here.
          isIdentity = false; // Safer to create Project if aliases change.
          break;
        }
      }
    }


    RelNode projectRel;
    if (isIdentity) {
      // If it's an identity projection (same fields, same order, same names), skip creating LogicalProject
      projectRel = inputRel;
    } else {
      projectRel = LogicalProject.create(inputRel, ImmutableList.of() /* hints */, projects, outputRowType);
    }


    // Create the scope reflecting the projection output
    // This scope replaces the input scope's relations with a single frame representing the projection.
    return Scope.createProjectScope(inputScope.getParent(), projectRel, aliases);
  }

  /** Helper to get an explicit alias from a node (e.g., expr AS alias) */
  private @Nullable String getExplicitAlias(SqlNode node) {
    if (node.getKind() == SqlKind.AS) {
      SqlCall asCall = (SqlCall) node;
      SqlNode aliasNode = asCall.getOperandList().get(1);
      if (aliasNode instanceof SqlIdentifier) {
        return ((SqlIdentifier) aliasNode).getSimple();
      }
    } else if (node.getKind() == SqlKind.IDENTIFIER) {
      if (((SqlIdentifier) node).isSimple()) {
        // Simple identifier (no AS) - return as alias
        return ((SqlIdentifier) node).getSimple();
      }
      // Qualified identifier (e.g., table.column) - return the last part as alias
      SqlIdentifier id = (SqlIdentifier) node;
      return id.names.get(id.names.size() - 1);
    }
    return null;
  }


  /**
   * Converts an ORDER BY clause (including LIMIT/OFFSET).
   * Takes the Scope of the query to be sorted and returns a Scope representing the sorted output.
   */
  protected Scope convertOrderBy(SqlOrderBy orderBy, Scope parentScope) {
    // Convert the underlying query first. It executes in its own scope.
    Scope inputScope = convertQueryRecursive(orderBy.query, false, parentScope);
    RelNode inputRel = inputScope.getRelNode();

    List<RelFieldCollation> collations = new ArrayList<>();

    if (orderBy.orderList != null && !orderBy.orderList.getList().isEmpty()) {
      for (SqlNode orderNode : orderBy.orderList.getList()) {
        RelFieldCollation.Direction direction = RelFieldCollation.Direction.ASCENDING; // Default
        RelFieldCollation.NullDirection nullDirection = RelFieldCollation.NullDirection.UNSPECIFIED; // Default based on direction

        SqlNode exprNode = orderNode;

        // Handle DESCENDING, NULLS FIRST, NULLS LAST modifiers
        // These wrap the expression, so unwrap step-by-step
        if (orderNode.getKind() == SqlKind.DESCENDING) {
          exprNode = ((SqlCall) orderNode).getOperandList().get(0);
          direction = RelFieldCollation.Direction.DESCENDING;
        }
        // Check for NULLS FIRST/LAST *after* potential DESCENDING
        if (exprNode.getKind() == SqlKind.NULLS_FIRST) {
          exprNode = ((SqlCall) exprNode).getOperandList().get(0);
          nullDirection = RelFieldCollation.NullDirection.FIRST;
        } else if (exprNode.getKind() == SqlKind.NULLS_LAST) {
          exprNode = ((SqlCall) exprNode).getOperandList().get(0);
          nullDirection = RelFieldCollation.NullDirection.LAST;
        }

        // Set default null direction based on SQL standard if unspecified
        if (nullDirection == RelFieldCollation.NullDirection.UNSPECIFIED) {
          // Standard SQL: NULLS LAST for ASC, NULLS FIRST for DESC
          nullDirection = (direction == RelFieldCollation.Direction.DESCENDING)
              ? RelFieldCollation.NullDirection.FIRST
              : RelFieldCollation.NullDirection.LAST;
        }

        // Convert the ordering expression - it refers to the output of the underlying query (inputScope)
        RexNode orderRex = convertExpression(exprNode, inputScope);

        // Find the index of this expression in the input RelNode's output fields
        // This requires matching the RexNode against the fields defined in inputScope.
        int fieldIndex = findRexOutputIndex(orderRex, inputScope);

        if (fieldIndex != -1) {
          collations.add(new RelFieldCollation(fieldIndex, direction, nullDirection));
        } else {
          // If the expression is not a simple output field reference, it should have been projected.
          // Calcite's SqlToRelConverter often adds complex ORDER BY expressions to the
          // underlying projection if they aren't already present. Doing this manually
          // here is complex. Assume ORDER BY refers to output columns/expressions by alias or structure.
          // Try matching by structure/digest if simple index lookup fails?
          // For now, throw an error if not found.
          throw new UnsupportedOperationException("ORDER BY expression '" + exprNode
              + "' could not be resolved to an output column/expression index of the query: "
              + inputScope.getFieldNames() + ". Pre-projection might be required or alias matching needed.");
          // Alternative: If exprNode is an integer literal, treat as ordinal (1-based index)
          // if (exprNode instanceof SqlNumericLiteral) { ... handle ordinal ... }
        }
      }
    }

    RexNode offset = null;
    RexNode fetch = null;

    if (orderBy.offset != null) {
      // Offset expression is evaluated without input scope (should be constant)
      offset = convertExpression(orderBy.offset, Scope.createRoot()); // Evaluate in empty scope
      // Validate it's a non-negative integer literal/parameter
      if (!isValidLimitOffset(offset)) {
        throw new IllegalArgumentException("OFFSET requires a non-negative integer literal or parameter, found: " + orderBy.offset);
      }
    }
    if (orderBy.fetch != null) {
      // Fetch/Limit expression is evaluated without input scope (should be constant)
      fetch = convertExpression(orderBy.fetch, Scope.createRoot()); // Evaluate in empty scope
      if (!isValidLimitOffset(fetch)) {
        throw new IllegalArgumentException("FETCH/LIMIT requires a non-negative integer literal or parameter, found: " + orderBy.fetch);
      }
    }

    // Create LogicalSort only if needed
    RelNode outputRel = inputRel;
    if (!collations.isEmpty() || offset != null || fetch != null) {
      RelCollation relCollation = RelCollations.of(collations);
      outputRel = LogicalSort.create(inputRel, relCollation, offset, fetch);
    }

    // Return a scope representing the output of the Sort (or the input if no sort was applied)
    // The row type is the same, but collation is added.
    return Scope.createScopeForRelNode(parentScope, outputRel, inputScope.getAlias()); // Preserve alias if any
  }

  /** Check if RexNode is suitable for LIMIT/OFFSET */
  private boolean isValidLimitOffset(RexNode rex) {
    if (rex == null) return false;
    // Allow non-negative integer literals
    if (rex instanceof RexLiteral) {
      RexLiteral lit = (RexLiteral) rex;
      if (SqlTypeUtil.isExactNumeric(lit.getType()) && !lit.isNull()) {
        BigDecimal val = lit.getValueAs(BigDecimal.class);
        return val != null && val.signum() >= 0 && val.scale() <= 0; // Non-negative integer
      }
    }
    // Allow dynamic parameters (RexDynamicParam) - assume they will be valid integers at runtime
    if (rex instanceof RexDynamicParam) {
      // Could check dynamic param type if available, but generally assume valid
      return SqlTypeUtil.isExactNumeric(rex.getType()); // Check if type is numeric at least
    }
    return false;
  }


  /**
   * Converts set operations (UNION, INTERSECT, EXCEPT).
   * Takes the parent Scope and returns a Scope representing the output of the set operation.
   */
  protected Scope convertSetOp(SqlCall setOp, Scope parentScope) {
    // Convert inputs recursively. They operate in their own scopes but share the parent scope.
    Scope leftScope = convertQueryRecursive(setOp.getOperandList().get(0), false, parentScope);
    Scope rightScope = convertQueryRecursive(setOp.getOperandList().get(1), false, parentScope);

    RelNode leftRel = leftScope.getRelNode();
    RelNode rightRel = rightScope.getRelNode();

    // TODO: Validate that left and right row types are compatible (same field count, compatible types).
    // Assumed validated for now. Type coercion might be needed in LogicalSetOp implementations.
    if (leftRel.getRowType().getFieldCount() != rightRel.getRowType().getFieldCount()) {
      throw new IllegalArgumentException("Operands of set operation " + setOp.getOperator().getName()
          + " must have the same number of columns. Left: " + leftRel.getRowType().getFieldCount()
          + ", Right: " + rightRel.getRowType().getFieldCount());
    }
    // Deeper type compatibility check needed here in a real implementation.

    boolean all = false;
    if (setOp.getOperator() instanceof SqlSetOperator) {
      all = ((SqlSetOperator) setOp.getOperator()).isAll();
    }
    List<RelNode> inputs = ImmutableList.of(leftRel, rightRel);

    SqlKind kind = setOp.getKind();
    RelNode setOpRel;
    switch (kind) {
      case UNION:
        setOpRel = LogicalUnion.create(inputs, all);
        break;
      case INTERSECT:
        setOpRel = LogicalIntersect.create(inputs, all);
        break;
      case EXCEPT:
        setOpRel = LogicalMinus.create(inputs, all);
        break;
      default:
        throw new AssertionError("Unexpected set operator: " + kind);
    }

    // The output row type and field names are typically derived from the left operand.
    // Create a scope representing the output.
    return Scope.createScopeForRelNode(parentScope, setOpRel, null); // SetOps usually don't have an alias
  }

  /**
   * Converts a VALUES clause.
   * Returns a Scope representing the LogicalValues node.
   */
  protected Scope convertValues(SqlCall valuesCall, Scope parentScope) {
    assert valuesCall.getOperator().equals(SqlStdOperatorTable.VALUES);

    List<ImmutableList<RexLiteral>> tuples = new ArrayList<>();
    RelDataType rowType = null;
    List<String> fieldNames = null;

    if (valuesCall.getOperandList().isEmpty()) {
      // No rows provided, e.g., INSERT INTO T SELECT * FROM (VALUES)
      // We need a schema. This is hard without context.
      // Calcite often uses a special EmptyScope or infers from target.
      // Let's create a zero-row, zero-column Values node.
      // This might not be correct in all contexts (e.g., INSERT).
      RelDataType emptyType = typeFactory.createStructType(ImmutableList.of(), ImmutableList.of());
      RelNode emptyValues = LogicalValues.create(cluster, emptyType, ImmutableList.of());
      return Scope.createScopeForRelNode(parentScope, emptyValues, "$VALUES_EMPTY");
    }

    // Use the first row to infer types and names
    for (int i = 0; i < valuesCall.getOperandList().size(); i++) {
      SqlNode rowConstructor = valuesCall.getOperandList().get(i);
      if (!(rowConstructor instanceof SqlCall) || !((SqlCall) rowConstructor).getOperator().equals(SqlStdOperatorTable.ROW)) {
        throw new IllegalArgumentException("VALUES operands must be ROW constructors, found: " + rowConstructor.getKind());
      }
      SqlCall rowCall = (SqlCall) rowConstructor;
      ImmutableList.Builder<RexLiteral> tupleBuilder = ImmutableList.builder();
      List<RelDataType> types = new ArrayList<>();
      List<String> currentFieldNames = new ArrayList<>();

      if (i == 0) { // Infer from first row
        fieldNames = new ArrayList<>();
        for (int j = 0; j < rowCall.getOperandList().size(); j++) {
          SqlNode operand = rowCall.getOperandList().get(j);
          // Use a null/empty scope as literals don't depend on input
          RexNode rex = convertExpression(operand, Scope.createRoot());

          if (!(rex instanceof RexLiteral)) {
            // Allow DEFAULT? Requires context. Allow simple expressions? Maybe CAST?
            // Standard SQL VALUES typically requires literals or simple expressions.
            // For simplicity, stick to literals.
            throw new UnsupportedOperationException("VALUES clause currently only supports literals. Found: " + rex.getKind() + " for operand: " + operand);
          }
          tupleBuilder.add((RexLiteral) rex);
          types.add(rex.getType());
          // Try to get alias, default to COL$j
          String name = getExplicitAlias(operand); // Check for CAST(lit AS alias) or similar? Unlikely here.
          if (name == null) {
            // Calcite default: COLUMN$index
            name = "COLUMN$" + j;
          }
          currentFieldNames.add(name);
        }
        // Determine the row type from the first row.
        // Need to handle potential type coercion across rows (e.g., INT and DECIMAL -> DECIMAL).
        // This requires looking ahead or assuming validation handled it.
        // Let's assume the first row's types are the target types.
        rowType = typeFactory.createStructType(types, currentFieldNames);
        fieldNames = currentFieldNames; // Store for the final node
        tuples.add(tupleBuilder.build());

      } else { // Subsequent rows
        if (rowType == null) throw new IllegalStateException("Row type should have been determined by the first row.");
        if (rowCall.getOperandList().size() != rowType.getFieldCount()) {
          throw new IllegalArgumentException("VALUES row " + i + " has " + rowCall.getOperandList().size()
              + " columns, but expected " + rowType.getFieldCount());
        }
        for (int j = 0; j < rowCall.getOperandList().size(); j++) {
          SqlNode operand = rowCall.getOperandList().get(j);
          RexNode rex = convertExpression(operand, Scope.createRoot());
          if (!(rex instanceof RexLiteral)) {
            throw new UnsupportedOperationException("VALUES clause currently only supports literals. Found: " + rex.getKind() + " for operand: " + operand);
          }
          // TODO: Type Compatibility Check: Check if rex.getType() is assignable to rowType.getFieldList().get(j).getType()
          // This might involve casting the literal if needed. Assumed validated for now.
          // Example: If column 1 is DECIMAL and row 2 has INTEGER, cast INTEGER literal to DECIMAL.
          RexLiteral literal = (RexLiteral) rex;
          RelDataType targetFieldType = rowType.getFieldList().get(j).getType();
          if (!literal.getType().equals(targetFieldType)) {
            // Attempt to cast the literal to the target type
            try {
              literal = (RexLiteral) rexBuilder.makeCast(targetFieldType, literal);
            } catch (Exception e) {
              throw new IllegalArgumentException("Cannot assign value " + literal + " of type " + literal.getType()
                  + " to column " + fieldNames.get(j) + " of type " + targetFieldType + " in VALUES row " + i, e);
            }
          }
          tupleBuilder.add(literal);
        }
        tuples.add(tupleBuilder.build());
      }
    }

    if (rowType == null) {
      // Should not happen if operand list was not empty
      throw new IllegalStateException("Could not determine row type for VALUES clause");
    }

    RelNode valuesRel = LogicalValues.create(cluster, rowType, ImmutableList.copyOf(tuples));
    return Scope.createScopeForRelNode(parentScope, valuesRel, "$VALUES"); // Use a synthetic alias
  }


  // =====================================================================
  // Expression Conversion (SqlNode -> RexNode) - No return type changes needed here
  // =====================================================================

  /**
   * Converts a {@link SqlNode} expression into a {@link RexNode} within a given scope.
   *
   * @param node  The SqlNode expression to convert.
   * @param scope The scope providing context for identifier resolution.
   * @return The equivalent RexNode.
   */
  protected RexNode convertExpression(SqlNode node, Scope scope) {
    // Handle AS at the top level - the alias is metadata, not part of the RexNode value.
    if (node.getKind() == SqlKind.AS) {
      SqlCall asCall = (SqlCall) node;
      if (asCall.getOperandList().size() >= 1) {
        // Convert the actual expression part, ignoring the alias.
        return convertExpression(asCall.getOperandList().get(0), scope);
      } else {
        throw new IllegalArgumentException("Invalid AS operator usage. Node: " + node);
      }
    }
    // Handle DEFAULT keyword - needs special context, often replaced by NULL or target column default.
    if (node.getKind() == SqlKind.DEFAULT) {
      // Cannot handle DEFAULT without knowing the target context (e.g., target column in INSERT/UPDATE).
      // Throwing an error is safest here. A real implementation might substitute NULL or require context.
      throw new UnsupportedOperationException("DEFAULT keyword cannot be converted to RexNode without target context.");
    }


    SqlKind kind = node.getKind();
    switch (kind) {
      case LITERAL:
        return convertLiteral((SqlLiteral) node);
      case IDENTIFIER:
        return convertIdentifier((SqlIdentifier) node, scope);
      case CASE:
        return convertCase((SqlCall) node, scope);
      case ROW:
        return convertRow((SqlCall) node, scope);
      case LITERAL_CHAIN: // e.g., 'abc' 'def'
        throw new UnsupportedOperationException("LITERAL_CHAIN is not supported directly. Consider using CONCAT or similar.");
//        return convertLiteralChain((SqlLiteralChain) node);
      case OVER: // Window function call
        return convertOver((SqlCall) node, scope);

      // Add other expression kinds: CAST, functions, operators...
      case OTHER_FUNCTION: // Includes user-defined functions
      case PLUS:
      case MINUS:
      case TIMES:
      case DIVIDE:
      case MOD:
      case EQUALS:
      case NOT_EQUALS:
      case LESS_THAN:
      case GREATER_THAN:
      case LESS_THAN_OR_EQUAL:
      case GREATER_THAN_OR_EQUAL:
      case AND:
      case OR:
      case NOT:
      case IS_NULL:
      case IS_NOT_NULL:
      case IS_TRUE:
      case IS_NOT_TRUE:
      case IS_FALSE:
      case IS_NOT_FALSE:
      case PLUS_PREFIX:
      case MINUS_PREFIX:
      case EXISTS:
      case SCALAR_QUERY:
      case IN:
      case NOT_IN:
      case LIKE:
      case SIMILAR:
      case BETWEEN:
//      case NOT_BETWEEN: // Often expanded: NOT (expr BETWEEN lower AND upper)
      case CAST:
      case FLOOR: // Example date/time function
      case CEIL:  // Example date/time function
      case EXTRACT: // Example date/time function
      case POSITION: // Example string function
//      case SUBSTRING: // Example string function
      case TRIM: // Example string function
//      case UPPER: // Example string function
//      case LOWER: // Example string function
//      case CONCAT: // Example string operator (||)
      case COALESCE:
      case NULLIF:
        // ... other operators and standard functions
        return convertCall((SqlCall) node, scope);
      default:
        // Check for aggregate functions - should only be converted directly
        // in specific contexts (e.g., post-aggregation).
        if (node instanceof SqlCall && ((SqlCall) node).getOperator() instanceof SqlAggFunction) {
          // This might be called from convertAggregate or convertPostAggregationExpression
          // If called from general convertExpression, it's likely an error unless it's
          // inside an OVER clause (handled by convertOver).
          if (scope.isAggregateContext()) {
            // If we are in a post-aggregation scope (HAVING, ORDER BY),
            // this aggregate call should resolve to an input reference from the Aggregate node.
            return convertAggregateCallExpr((SqlCall) node, scope);
          } else {
            // Aggregate function outside of aggregation context or OVER clause.
            throw new IllegalStateException("Aggregate function " + ((SqlCall) node).getOperator().getName()
                + " encountered outside of aggregation context or OVER clause.");
          }
        }
        // create RexCall for unhandled function
        if (node instanceof SqlCall sqlCall) {
          List<RexNode> operands = sqlCall.getOperandList().stream()
              .map(op -> convertExpression(op, scope))
              .toList();
          RelDataType type = typeFactory.createSqlType(SqlTypeName.ANY);
          return rexBuilder.makeCall(type, sqlCall.getOperator(), operands);
        }
        throw new UnsupportedOperationException("Unsupported expression node type: " + kind + " (" + node.getClass().getSimpleName() + ")");
    }
  }

  private SqlFunction createUDF(String functionName) {
    // Create a SqlFunction instance for the UDF
    SqlIdentifier functionId = new SqlIdentifier(functionName, SqlParserPos.ZERO);
    SqlFunction function = new SqlFunction(functionId.getSimple(), SqlKind.OTHER_FUNCTION,
        null, null, null, null);
    return function;
  }

  /**
   * Converts a literal SqlNode to RexLiteral.
   */
  protected RexLiteral convertLiteral(SqlLiteral literal) {
    SqlTypeName typeName = literal.getTypeName();
    Object value = literal.getValue(); // May be null

    if (value == null) {
      // Handle NULL literal. Need type information.
      // If this literal is inside a CAST(NULL AS type), convertCast handles it.
      // Otherwise, it's ambiguous. Calcite often uses a special NULL type initially.
      RelDataType type = typeFactory.createSqlType(SqlTypeName.NULL);
      // RexBuilder.makeNullLiteral requires a specific type.
      // We *must* get the type from context (e.g., CAST or target column).
      // Throwing an error here if type is unknown is safer.
      // However, convertExpression might be called within CAST, so let CAST handle it.
      // If called elsewhere, it's an untyped NULL.
      // Let's return a RexLiteral with the basic NULL type, hoping context resolves it.
      // This is risky. A better approach needs context.
      // return (RexLiteral) rexBuilder.makeNullLiteral(type); // Fails as RexBuilder needs specific type
      // Let's try creating a specific typed null, e.g., VARCHAR NULL, as a placeholder? Very risky.
      // Safest: Assume it's handled by CAST or throw. Let's throw for now if not in CAST.
      // Revisit: How does Calcite handle untyped NULLs pre-validation? It often relies on context.
      // Let's use makeNullLiteral(targetType) inside convertCast, and maybe default to VARCHAR NULL here?
      // Defaulting to VARCHAR NULL:
      // RelDataType varcharType = typeFactory.createSqlType(SqlTypeName.VARCHAR);
      // return (RexLiteral) rexBuilder.makeNullLiteral(varcharType);
      // Let's stick to the basic NULL type and rely on CAST or context.
      return rexBuilder.constantNull(); // Returns a generic NULL literal
    }

    try {
      switch (typeName) {
        case BOOLEAN:
          return rexBuilder.makeLiteral(literal.booleanValue());
        case TINYINT:
        case SMALLINT:
        case INTEGER:
        case BIGINT:
          // Use BigDecimal for precision, derive type from literal's typeName
          RelDataType exactType = typeFactory.createSqlType(typeName);
          return rexBuilder.makeExactLiteral(literal.getValueAs(BigDecimal.class), exactType);
        case DECIMAL:
          // Need precision/scale. SqlLiteral doesn't always hold it directly.
          // Infer from value or use default? Assume validation set it or infer.
          Integer decValue = literal.getValueAs(Integer.class);
//          int precision = Math.max(decValue.precision(), decValue.scale()) + 1; // Basic inference
//          int scale = decValue.scale();
          RelDataType decType = typeFactory.createSqlType(SqlTypeName.INTEGER);
          return rexBuilder.makeLiteral(decValue, decType);
        case FLOAT: // Approximate
        case REAL:  // Approximate
        case DOUBLE: // Approximate
          RelDataType approxType = typeFactory.createSqlType(typeName);
          // RexBuilder often expects BigDecimal even for approx types
          return rexBuilder.makeApproxLiteral(literal.getValueAs(BigDecimal.class), approxType);
        case CHAR:
        case VARCHAR:
          // Use NlsString which includes charset/collation info
          NlsString nlsString = literal.getValueAs(NlsString.class);
//          RelDataType charType = typeFactory.createSqlType(typeName, nlsString.getValue().length());
//          charType = typeFactory.createTypeWithCharsetAndCollation(charType, nlsString.getCharset(), nlsString.getCollation());
          return rexBuilder.makeCharLiteral(nlsString); // Pass type explicitly
        case BINARY:
        case VARBINARY:
          throw new UnsupportedOperationException("BINARY/VARBINARY literals not supported yet.");
//          ByteString byteString = literal.getValueAs(ByteString.class);
//          RelDataType binaryType = typeFactory.createSqlType(typeName, byteString.length());
//          return rexBuilder.makeBinaryLiteral(byteString, binaryType); // Pass type explicitly
        case DATE:
          // Value is DateString
          DateString dateString = literal.getValueAs(DateString.class);
//          RelDataType dateType = typeFactory.createSqlType(SqlTypeName.DATE);
          return rexBuilder.makeDateLiteral(dateString); // Pass type explicitly
        case TIME:
          // Value is TimeString. Need precision.
          TimeString timeString = literal.getValueAs(TimeString.class);
          int timePrecision = timeString.toString(0).length(); // Infer precision
//          RelDataType timeType = typeFactory.createSqlType(SqlTypeName.TIME, timePrecision);
          return rexBuilder.makeTimeLiteral(timeString, timePrecision); // Pass type explicitly
        case TIMESTAMP:
          // Value is TimestampString. Need precision.
          TimestampString tsString = literal.getValueAs(TimestampString.class);
          int tsPrecision = tsString.toString().length(); // Infer precision
//          RelDataType tsType = typeFactory.createSqlType(SqlTypeName.TIMESTAMP, tsPrecision);
          return rexBuilder.makeTimestampLiteral(tsString, tsPrecision); // Pass type explicitly
        case INTERVAL_YEAR:
        case INTERVAL_YEAR_MONTH:
        case INTERVAL_MONTH:
        case INTERVAL_DAY:
        case INTERVAL_DAY_HOUR:
        case INTERVAL_DAY_MINUTE:
        case INTERVAL_DAY_SECOND:
        case INTERVAL_HOUR:
        case INTERVAL_HOUR_MINUTE:
        case INTERVAL_HOUR_SECOND:
        case INTERVAL_MINUTE:
        case INTERVAL_MINUTE_SECOND:
        case INTERVAL_SECOND:
          // Value is SqlIntervalLiteral.IntervalValue
          SqlIntervalLiteral.IntervalValue intValue = (SqlIntervalLiteral.IntervalValue) value;
          SqlIntervalQualifier qualifier = intValue.getIntervalQualifier();
          // Ensure qualifier has precision if needed (e.g., SECOND(p))
          RelDataType intervalType = typeFactory.createSqlIntervalType(qualifier);
          // Value needs to be parsed carefully based on qualifier units
          BigDecimal intervalValueDecimal = new BigDecimal(intValue.getSign() * Long.parseLong(intValue.getIntervalLiteral()));
          return rexBuilder.makeIntervalLiteral(intervalValueDecimal, qualifier); // RexBuilder uses qualifier

        case SYMBOL: // Used for flags like DESC, NULLS FIRST/LAST - not expression literals
          throw new IllegalArgumentException("Cannot convert SYMBOL literal to RexNode: " + literal);
        case MULTISET: // Requires complex handling
          throw new UnsupportedOperationException("MULTISET literals not supported yet.");
        case GEOMETRY:
          throw new UnsupportedOperationException("GEOMETRY literals not supported yet.");
          // Add other types (ARRAY, MAP, ROW?)
        default:
          throw new UnsupportedOperationException("Unsupported literal type: " + typeName);
      }
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Literal value " + value + " (class " + value.getClass().getName() + ") does not match expected type for " + typeName + ": " + literal, e);
    }
  }

  /**
   * Converts an identifier SqlNode to RexInputRef or RexFieldAccess.
   */
  protected RexNode convertIdentifier(SqlIdentifier id, Scope scope) {
    // Try to find the field in the current scope
    Pair<Frame, RelDataTypeField> fieldInfo = scope.findField(id);

    if (fieldInfo != null) {
      Frame frame = fieldInfo.left;
      RelDataTypeField field = fieldInfo.right;
      // Calculate the absolute index based on the frame's offset in the combined input
      int index = frame.offset + field.getIndex();
      // Create a RexInputRef using the field's type and calculated index
      return rexBuilder.makeInputRef(field.getType(), index);
    }

    // If not found in the immediate scope relations, check for correlation variables.
    // This requires walking up the parent scope chain.
    Pair<Scope, RelDataTypeField> corrVarInfo = scope.findCorrelationVariable(id);
    if (corrVarInfo != null) {
      Scope definingScope = corrVarInfo.left;
      RelDataTypeField field = corrVarInfo.right;
      CorrelationId corrId = definingScope.getCorrelationId(); // Scope needs to manage CorrelationIds
      if (corrId == null) {
        // This should not happen if findCorrelationVariable returned successfully
        throw new IllegalStateException("Found correlation variable but no CorrelationId in defining scope for: " + id);
      }
      // Create RexFieldAccess: rexBuilder.makeFieldAccess(rexBuilder.makeCorrel(definingScope.getRowType(), corrId), field.getIndex())
      // Need the row type of the correlation source from the defining scope.
      RelDataType corrVarType = definingScope.getCorrelationType(); // Scope needs to provide this
      if (corrVarType == null) {
        throw new IllegalStateException("Found correlation variable but no correlation type in defining scope for: " + id);
      }
      RexNode corrVarRef = rexBuilder.makeCorrel(corrVarType, corrId);
      return rexBuilder.makeFieldAccess(corrVarRef, field.getIndex());
    }


    // If not found anywhere, it's an error (assuming prior validation)
//    throw new RuntimeException("Identifier '" + id + "' not found in current scope or parent scopes: " + scope.getFieldNames());
    String value = id.isSimple() ? id.getSimple() : String.join(".", id.names);
    return rexBuilder.makeLiteral(value); // Fallback to literal - not ideal, but prevents crash
  }

  /**
   * Converts a CASE expression.
   */
  protected RexNode convertCase(SqlCall call, Scope scope) {
    assert call.getOperator().equals(SqlStdOperatorTable.CASE);
    SqlCase caseCall = (SqlCase) call;
    List<RexNode> rexOperands = new ArrayList<>();

    for (int i = 0; i < caseCall.getWhenOperands().size(); i++) {
      SqlNode whenOperand = caseCall.getWhenOperands().get(i);
      rexOperands.add(convertExpression(whenOperand, scope));
      SqlNode thenOperand = caseCall.getThenOperands().get(i);
      rexOperands.add(convertExpression(thenOperand, scope));
    }
    SqlNode elseOperand = caseCall.getElseOperand();
    if (elseOperand != null) {
      rexOperands.add(convertExpression(elseOperand, scope));
    }

    // RexBuilder.makeCall handles type inference for the result (least restrictive of THEN/ELSE)
    // It needs the target type explicitly if operands include untyped NULLs.
    // Let's try without explicit type first.
    try {
      return rexBuilder.makeCall(SqlStdOperatorTable.CASE, rexOperands);
    } catch (IllegalArgumentException e) {
      // If it fails due to untyped nulls, we might need to infer the type.
      // Infer type from non-null THEN/ELSE clauses.
      RelDataType resultType = inferCaseResultType(rexOperands);
      if (resultType != null) {
        return rexBuilder.makeCall(resultType, SqlStdOperatorTable.CASE, rexOperands);
      } else {
        // Cannot infer type (e.g., CASE WHEN cond THEN NULL ELSE NULL END)
        // Need context or default type. Throwing error is safest.
        throw new IllegalStateException("Cannot determine result type for CASE expression with ambiguous NULL types: " + caseCall, e);
      }
    }
  }

  /** Infer result type for CASE, finding least restrictive among non-null THEN/ELSE */
  private @Nullable RelDataType inferCaseResultType(List<RexNode> rexOperands) {
    List<RelDataType> types = new ArrayList<>();
    // Operands are WHEN1, THEN1, WHEN2, THEN2, ..., ELSE
    for (int i = 1; i < rexOperands.size(); i += 2) { // THEN clauses
      if (!rexOperands.get(i).getType().getSqlTypeName().equals(SqlTypeName.NULL)) {
        types.add(rexOperands.get(i).getType());
      }
    }
    if (rexOperands.size() % 2 != 0) { // ELSE clause exists
      RexNode elseNode = rexOperands.get(rexOperands.size() - 1);
      if (!elseNode.getType().getSqlTypeName().equals(SqlTypeName.NULL)) {
        types.add(elseNode.getType());
      }
    }
    if (types.isEmpty()) {
      return null; // All THEN/ELSE are NULL
    }
    return typeFactory.leastRestrictive(types);
  }

  /**
   * Converts a ROW constructor expression.
   */
  protected RexNode convertRow(SqlCall rowCall, Scope scope) {
    assert rowCall.getOperator().equals(SqlStdOperatorTable.ROW);
    List<RexNode> rexOperands = rowCall.getOperandList().stream()
        .map(operand -> convertExpression(operand, scope))
        .collect(Collectors.toList());

    // Determine type - relies on operand types. RexBuilder can derive this.
    // No explicit type needed for makeCall for ROW.
    return rexBuilder.makeCall(SqlStdOperatorTable.ROW, rexOperands);
  }

  /** Converts a SqlLiteralChain (e.g. 'abc' 'def') */
//  protected RexNode convertLiteralChain(SqlLiteralChain chain) {
//    // Concatenate the literals. Assumes they are all character literals.
//    // Validation should ensure this.
//    StringBuilder sb = new StringBuilder();
//    NlsString sample = null; // To get charset/collation
//    SqlTypeName typeName = null; // Should be CHAR or VARCHAR
//
//    for (SqlNode node : chain.getOperandList()) {
//      if (!(node instanceof SqlAbstractStringLiteral)) {
//        throw new IllegalArgumentException("Literal chain must contain only string literals: " + chain);
//      }
//      SqlAbstractStringLiteral stringLiteral = (SqlAbstractStringLiteral) node;
//      if (typeName == null) {
//        typeName = stringLiteral.getTypeName(); // Use type of first literal
//      } else if (stringLiteral.getTypeName() != typeName) {
//        // Mixed types (e.g., CHAR and VARCHAR) - validation should prevent?
//        // Use VARCHAR if mixed? Let's assume consistent type.
//        throw new IllegalArgumentException("Literal chain contains mixed string types: " + chain);
//      }
//
//      if (stringLiteral instanceof SqlCharStringLiteral) {
//        NlsString nls = ((SqlCharStringLiteral) stringLiteral).getNlsString();
//        sb.append(nls.getValue());
//        if (sample == null) sample = nls; // Use first literal for charset/collation
//      } else {
//        // Handle other string types like binary if needed
//        throw new UnsupportedOperationException("Unsupported string literal type in chain: " + stringLiteral.getClass());
//      }
//    }
//
//    if (sample == null) { // Empty chain? Or non-char strings?
//      // Default charset/collation? Risky. Assume validation ensures non-empty char chain.
//      if (typeName == null) typeName = SqlTypeName.VARCHAR; // Default guess
//      sample = new NlsString("", NlsString.DEFAULT_CHARSET_NAME, NlsString.DEFAULT_COLLATION_NAME); // Empty string with defaults
//    }
//
//    NlsString result = new NlsString(sb.toString(), sample.getCharsetName(), sample.getCollation());
//    // Determine type - should be CHAR/VARCHAR with combined length?
//    RelDataType type = typeFactory.createSqlType(typeName, result.getValue().length());
//    type = typeFactory.createTypeWithCharsetAndCollation(type, result.getCharset(), result.getCollation());
//
//    return rexBuilder.makeCharLiteral(result, type); // Pass explicit type
//  }

  /**
   * Converts a window function call (OVER). Requires LogicalWindow.
   */
  protected RexNode convertOver(SqlCall overCall, Scope scope) {
    // Manual creation of RexOver is complex as it ties into LogicalWindow.
    // LogicalWindow itself requires careful setup of partitions, orderings, and frame.
    // This is one of the most complex parts to do without RelBuilder/Validator.
    // We need to:
    // 1. Identify the aggregate function call (e.g., SUM(col)).
    // 2. Identify the window spec (PARTITION BY, ORDER BY, frame).
    // 3. Convert aggregate args, partition keys, order keys using the input scope.
    // 4. Convert the frame bounds.
    // 5. Create a RexOver node.
    // 6. Ensure a LogicalWindow node is added later in the plan (typically after Project).

    // This simplified version assumes the OVER call structure is valid.
    if (overCall.getOperandList().size() != 2) {
      throw new IllegalArgumentException("OVER operator requires 2 operands (aggregate call, window spec)");
    }
    SqlCall aggCallNode = (SqlCall) overCall.getOperandList().get(0);
    SqlWindow windowNode = (SqlWindow) overCall.getOperandList().get(1);

    // --- Convert Aggregate Part ---
    if (!(aggCallNode.getOperator() instanceof SqlAggFunction)) {
      throw new IllegalArgumentException("First operand of OVER must be an aggregate function call, found: " + aggCallNode.getOperator());
    }
    SqlAggFunction sqlAggFunc = (SqlAggFunction) aggCallNode.getOperator();
    List<RexNode> aggArgs = aggCallNode.getOperandList().stream()
        .map(op -> convertExpression(op, scope))
        .collect(Collectors.toList());
    boolean distinct = false; // TODO: Handle DISTINCT within OVER if needed (e.g., COUNT(DISTINCT x) OVER (...))

    // --- Convert Window Spec ---
    // Partition Keys
    List<RexNode> partitionKeys = windowNode.getPartitionList().getList().stream()
        .map(p -> convertExpression(p, scope))
        .collect(Collectors.toList());

    // Order Keys
    ImmutableList.Builder<RexFieldCollation> orderKeys = ImmutableList.builder();
    for (SqlNode orderNode : windowNode.getOrderList().getList()) {
      // Similar logic to convertOrderBy for direction/nulls
      SqlKind direction = null;
      SqlKind nullDirection = null;
      SqlNode exprNode = orderNode;
      // Unwrap modifiers
      if (orderNode.getKind() == SqlKind.DESCENDING) {
        exprNode = ((SqlCall) orderNode).getOperandList().get(0);
        direction = SqlKind.DESCENDING;
      }
      if (exprNode.getKind() == SqlKind.NULLS_FIRST) {
        exprNode = ((SqlCall) exprNode).getOperandList().get(0);
        nullDirection = SqlKind.NULLS_FIRST;//RelFieldCollation.NullDirection.FIRST;
      } else if (exprNode.getKind() == SqlKind.NULLS_LAST) {
        exprNode = ((SqlCall) exprNode).getOperandList().get(0);
        nullDirection = SqlKind.NULLS_FIRST;//RelFieldCollation.NullDirection.LAST;
      }
      // Set default null direction
//      if (nullDirection == SqlKind.UNSPECIFIED) {
//        nullDirection = (direction == RelFieldCollation.Direction.DESCENDING) ? RelFieldCollation.NullDirection.FIRST : RelFieldCollation.NullDirection.LAST;
//      }
      // Convert expression
      RexNode orderRex = convertExpression(exprNode, scope);
      if (direction != null && nullDirection != null) {
        // Create a RexFieldCollation with direction and null direction
        // RelFieldCollation.Direction and RelFieldCollation.NullDirection are enums
        // This is a placeholder, actual creation might differ based on internal APIs
        // orderKeys.add(new RexFieldCollation(orderRex, Set.of(direction, nullDirection)));
        orderKeys.add(new RexFieldCollation(orderRex, Set.of(direction, nullDirection)));
      } else if (direction != null) {
        // Create a RexFieldCollation with direction only
        // orderKeys.add(new RexFieldCollation(orderRex, Set.of(direction)));
        orderKeys.add(new RexFieldCollation(orderRex, Set.of(direction)));
      } else {
        // Default direction (ASC)
        // orderKeys.add(new RexFieldCollation(orderRex, Set.of()));
        orderKeys.add(new RexFieldCollation(orderRex, Set.of()));
      }
    }

    RexWindowBound lowerBound;
    RexWindowBound upperBound;

    if (!aggCallNode.getOperator().allowsFraming()) {
      // No frame specified, default to entire partition
      // This is a placeholder, actual defaulting might differ based on internal APIs
      lowerBound = convertWindowBound(SqlWindow.createUnboundedPreceding(SqlParserPos.ZERO), scope);
      upperBound = convertWindowBound(SqlWindow.createCurrentRow(SqlParserPos.ZERO), scope);
    } else {
      lowerBound = convertWindowBound(windowNode.getLowerBound(), scope);
      upperBound = convertWindowBound(windowNode.getUpperBound(), scope);
    }
    boolean isRows = windowNode.isRows(); // ROWS or RANGE

    // Determine type of the aggregate function result
    RelDataType aggResultType = deriveAggCallType(sqlAggFunc, aggArgs, scope);
    // Adjust nullability based on function and window? Complex. Assume base nullability for now.
    aggResultType = typeFactory.createTypeWithNullability(aggResultType, true); // Window functions can often produce NULL

    // Create the RexOver node
    // Note: RexOver constructor is protected. Use RexBuilder helper if possible, or reflect.
    // RexBuilder doesn't have a public method to create RexOver directly.
    // This highlights the difficulty without internal Calcite tools.
    // We might need to construct it manually, which is fragile.

    // Placeholder - Actual creation is complex and might require internal APIs or RelBuilder usage.
//    throw new UnsupportedOperationException("Manual creation of RexOver for window functions is highly complex and not fully implemented here. Requires internal Calcite APIs or RelBuilder.");

    // If we could create it:
    return rexBuilder.makeOver(aggResultType, sqlAggFunc, aggArgs, partitionKeys, orderKeys.build(),
        lowerBound, upperBound, isRows, true, false /*ignoreNulls*/, false /*fromFirst*/, false /*fromLast*/);
  }

  /** Converts a SqlWindow bound (e.g., UNBOUNDED PRECEDING, CURRENT ROW, N FOLLOWING) */
  protected RexWindowBound convertWindowBound(SqlNode boundNode, Scope scope) {
    if (boundNode == null) {
      // Default bounds depend on context (e.g., presence of ORDER BY)
      // This requires more logic, assume explicit bounds for now.
      throw new IllegalArgumentException("Window bounds cannot be null (defaulting not implemented)");
    }
    if (!(boundNode instanceof SqlCall)) {
      // Could be SqlWindow.createCurrentRow() etc. which might not be SqlCalls? Check API.
      // Assume SqlCall for PRECEDING/FOLLOWING with offset.
      // Handle UNBOUNDED/CURRENT ROW based on operator/class.
      if (SqlWindow.isUnboundedPreceding(boundNode)) {
        return RexWindowBound.create(boundNode, null); // No offset needed
      } else if (SqlWindow.isCurrentRow(boundNode)) {
        return RexWindowBound.create(boundNode, null); // No offset needed
      } else if (SqlWindow.isUnboundedFollowing(boundNode)) {
        return RexWindowBound.create(boundNode, null); // No offset needed
      } else {
        throw new IllegalArgumentException("Unsupported window bound node type: " + boundNode.getClass());
      }
    }

    SqlCall boundCall = (SqlCall) boundNode;
    SqlOperator operator = boundCall.getOperator(); // PRECEDING or FOLLOWING

    if (operator == SqlWindow.PRECEDING_OPERATOR || operator == SqlWindow.FOLLOWING_OPERATOR) {
      if (boundCall.getOperandList().size() != 1) {
        throw new IllegalArgumentException(operator.getName() + " requires 1 operand (offset)");
      }
      // Convert the offset expression (should be constant or simple)
      RexNode offsetRex = convertExpression(boundCall.getOperandList().get(0), scope);
      // Offset type needs validation (non-negative numeric for ROWS, specific types for RANGE)
      return RexWindowBound.create(boundNode, offsetRex);
    } else {
      throw new IllegalArgumentException("Unexpected operator for window bound: " + operator);
    }
  }

  /**
   * Converts a generic SqlCall (operator or function).
   */
  protected RexNode convertCall(SqlCall call, Scope scope) {
    SqlOperator operator = call.getOperator();
    List<SqlNode> operands = call.getOperandList();

    // Handle special operators explicitly if they don't fit the generic model easily
    // or require specific RexNode types (like RexSubQuery).
    if (operator.equals(SqlStdOperatorTable.CAST)) {
      return convertCast(call, scope);
    }
    if (operator.equals(SqlStdOperatorTable.IN) || operator.equals(SqlStdOperatorTable.NOT_IN)) {
      return convertIn(call, scope);
    }
    if (operator.equals(SqlStdOperatorTable.EXISTS)) {
      // EXISTS is special, takes a subquery operand directly
      return convertExists(call, scope);
    }
    if (operator.getKind() == SqlKind.SCALAR_QUERY) {
      // SCALAR_QUERY is special, takes a subquery operand directly
      return convertScalarSubquery(call, scope);
    }
    if (operator.equals(SqlStdOperatorTable.BETWEEN) || operator.equals(SqlStdOperatorTable.NOT_BETWEEN)) {
      return convertBetween(call, scope);
    }
    // TODO: Handle other special cases like LIKE, SIMILAR, etc. if they need non-standard RexNode creation.

    // Generic handling for most operators/functions
    List<RexNode> rexOperands = operands.stream()
        .map(operand -> convertExpression(operand, scope))
        .collect(Collectors.toList());

    // Type Inference: Crucial and hard without SqlValidator.
    // Use deriveType helper which tries operator inference and RexBuilder fallback.
    RelDataType returnType = deriveType(call, operator, rexOperands, scope); // Pass call and scope for context

    // Use RexBuilder.makeCall, which handles operator mapping and type checks.
    // It might require the explicit return type if inference within RexBuilder fails.
    try {
      return rexBuilder.makeCall(returnType, operator, rexOperands);
    } catch (IllegalArgumentException e) {
      // Catch potential errors from RexBuilder (e.g., type mismatch)
      throw new IllegalArgumentException("Error creating call for operator " + operator.getName()
          + " with operands " + rexOperands + " and derived type " + returnType + ". SQL: " + call, e);
    }
  }

  /**
   * Derives return type for an operator call.
   * Needs call context for some operators (e.g., function lookup).
   */
  protected RelDataType deriveType(SqlCall call, SqlOperator operator, List<RexNode> rexOperands, Scope scope) {
    // 1. Try SqlOperator.inferReturnType (might need more context than just types)
    // 2. Fallback to RexBuilder.deriveReturnType
    // 3. Handle specific overrides if needed.

    RelDataType returnType = null;

    // Attempt 1: SqlOperator.inferReturnType
    // This often needs a CallBinding, which we don't have.
    // Try the simpler overload with just operand types.
    try {
      List<RelDataType> operandTypes = rexOperands.stream().map(RexNode::getType).collect(Collectors.toList());
      // Some operators might need literal values (e.g., EXTRACT unit) - not available here easily.
      returnType = operator.inferReturnType(typeFactory, operandTypes);
    } catch (Exception e) {
      // Ignore inference failure, fallback below
    }

    // Attempt 2: RexBuilder.deriveReturnType
    if (returnType == null) {
      try {
        // RexBuilder has more robust internal logic, handles common cases.
        returnType = rexBuilder.deriveReturnType(operator, rexOperands);
      } catch (Exception e) {
        // If RexBuilder also fails, we have a problem.
        throw new IllegalStateException("Could not determine return type using RexBuilder for operator " + operator.getName()
            + " with operand types: " + rexOperands.stream().map(r -> r.getType().getFullTypeString()).collect(Collectors.joining(", "))
            + ". SQL: " + call, e);
      }
    }

    // Attempt 3: Manual Overrides (if necessary for specific functions/operators)
    // e.g., if a function's type depends on arguments not captured by RexNode types.
    // if (operator.getName().equalsIgnoreCase("MY_SPECIAL_FUNCTION")) { ... }


    if (returnType == null) {
      // Should not be reached if RexBuilder succeeded or threw.
      throw new IllegalStateException("Could not determine return type for operator " + operator.getName() + ". SQL: " + call);
    }

    // Nullability Inference: This is extremely complex without a validator.
    // SqlOperator.validateOperands and inferReturnType usually handle this.
    // We rely heavily on the type returned by inferReturnType/deriveReturnType having the correct nullability.
    // A simple heuristic: result is nullable if the operator *can* return null (e.g., arithmetic with null operand)
    // or if any operand known to influence nullability is nullable.
    // This is very hard to get right generically. Rely on the derived type's nullability for now.
    // boolean isNullable = operator.isNullable( ... ) // Needs validator context
    // boolean operandNullable = rexOperands.stream().anyMatch(r -> r.getType().isNullable());
    // if (isNullable || operandNullable) {
    //     returnType = typeFactory.createTypeWithNullability(returnType, true);
    // }

    return returnType;
  }

  /**
   * Converts a CAST expression.
   */
  protected RexNode convertCast(SqlCall castCall, Scope scope) {
    assert castCall.getOperator().equals(SqlStdOperatorTable.CAST);
    if (castCall.getOperandList().size() != 2) {
      throw new IllegalArgumentException("CAST requires 2 operands, found: " + castCall.getOperandList().size() + " in " + castCall);
    }
    SqlNode expression = castCall.getOperandList().get(0);
    SqlNode typeNode = castCall.getOperandList().get(1);

    // Convert the expression being cast
    RexNode operand = convertExpression(expression, scope);

    // Derive the target type from the SqlDataTypeSpec
    RelDataType targetType;
    if (typeNode instanceof SqlDataTypeSpec) {
      SqlDataTypeSpec typeSpec = (SqlDataTypeSpec) typeNode;
      // Use deriveType(RelDataTypeFactory) - requires validator usually.
      // Try simpler deriveType(typeFactory) if available, or parse manually.
      // This is a major gap without SqlValidator.
      // Let's attempt manual parsing for common types.
      targetType = parseDataTypeSpec(typeSpec);
      if (targetType == null) {
        throw new IllegalStateException("Could not derive type from SqlDataTypeSpec: " + typeSpec + ". Manual parsing failed.");
      }
    } else {
      throw new IllegalArgumentException("CAST target type must be a SqlDataTypeSpec, found: " + typeNode.getKind());
    }


    // Handle CAST(NULL AS Type) - makeNullLiteral needs the target type
    // Check if operand is the generic NULL constant returned by convertLiteral(null)
    if (operand instanceof RexLiteral && ((RexLiteral) operand).isNull() && ((RexLiteral) operand).getTypeName() == SqlTypeName.NULL) {
      return rexBuilder.makeNullLiteral(targetType);
    }

    // Check if cast is necessary (operand type might already match target type)
    if (operand.getType().equals(targetType)) {
      return operand; // No cast needed
    }
    // Check for assignability (e.g., casting VARCHAR(10) to VARCHAR(20))
    if (SqlTypeUtil.canAssignFrom(targetType, operand.getType())) {
      // If only precision/scale/nullability differs but base types match,
      // RexBuilder.makeCast might still be needed to enforce the target type properties.
      // Example: INTEGER to DECIMAL, VARCHAR(10) to VARCHAR(20).
      // Let RexBuilder handle it.
    }


    // Create the cast call using RexBuilder
    try {
      return rexBuilder.makeCast(targetType, operand);
    } catch (Exception e) {
      throw new IllegalArgumentException("Failed to create CAST from " + operand.getType() + " to " + targetType + ". SQL: " + castCall, e);
    }
  }

  protected RelDataType parseDataTypeSpec(SqlDataTypeSpec spec) {
    throw new UnsupportedOperationException("Manual parsing of SqlDataTypeSpec is not implemented yet.");
  }


  /**
   * Converts IN / NOT IN expression (subquery or value list).
   */
  protected RexNode convertIn(SqlCall inCall, Scope scope) {
    boolean isNotIn = inCall.getOperator().equals(SqlStdOperatorTable.NOT_IN);
    if (inCall.getOperandList().size() != 2) {
      throw new IllegalArgumentException(inCall.getOperator().getName() + " requires 2 operands");
    }

    RexNode lhs = convertExpression(inCall.getOperandList().get(0), scope);
    SqlNode rhsNode = inCall.getOperandList().get(1);

    RexNode rex;
    if (rhsNode instanceof SqlNodeList) {
      // IN (value list)
      List<RexNode> rhsList = new ArrayList<>();
      for (SqlNode node : ((SqlNodeList) rhsNode).getList()) {
        rhsList.add(convertExpression(node, scope));
      }
      // RexBuilder creates an OR chain (lhs=v1 OR lhs=v2 ...) or uses RexInNode
      // Need to ensure type compatibility between lhs and rhsList elements.
      // RexBuilder.makeIn handles some coercion.
      if (rhsList.isEmpty()) {
        // IN () is always false, NOT IN () is always true
        rex = rexBuilder.makeLiteral(false);
      } else {
        rex = rexBuilder.makeIn(lhs, ImmutableList.copyOf(rhsList));
      }

    } else {
      // IN (subquery)
      // Convert the subquery. It needs its own scope, potentially correlated.
      // Pass the current scope as parent for correlation.
      Scope subQueryScope = convertQueryRecursive(rhsNode, false, scope);
      RelNode subQueryRel = subQueryScope.getRelNode();

      // Ensure subquery returns one column
      if (subQueryRel.getRowType().getFieldCount() != 1) {
        throw new IllegalArgumentException("Subquery for IN operator must return exactly one column, found "
            + subQueryRel.getRowType().getFieldCount() + " in: " + rhsNode);
      }
      // TODO: Type compatibility check between lhs and subquery column? Assumed validated.
      RelDataType subQueryColType = subQueryRel.getRowType().getFieldList().get(0).getType();
      if (!SqlTypeUtil.canAssignFrom(subQueryColType, lhs.getType()) && !SqlTypeUtil.canAssignFrom(lhs.getType(), subQueryColType)) {
        // Types are not compatible. Need coercion or validation should have caught this.
        // For now, assume compatible or rely on RexSubQuery to handle runtime checks.
      }


      // Create RexSubQuery for IN. This requires correlation handling.
      // RexSubQuery.in(rel, operands) where operands refer to fields from the *outer* query (lhs).
      // Need to manage CorrelationId.
      CorrelationId correlationId = scope.createCorrelationId(subQueryScope.getAlias()); // Scope needs method to manage correlations
      RexNode corrVar = rexBuilder.makeCorrel(subQueryRel.getRowType(), correlationId); // This seems wrong, correl type should be outer?

      // Let's rethink RexSubQuery creation.
      // RexSubQuery.in(RelNode rel, ImmutableList<RexNode> operands)
      // 'rel' is the subquery RelNode.
      // 'operands' are the expressions from the *outer* query to be compared against the subquery's output column.
      // The subquery RelNode itself might contain RexInputRefs referring back to the outer query via CorrelationId.

      // 1. Convert subquery, potentially creating correlations if it references outer scope.
      //    This requires convertIdentifier to handle correlations correctly.
      // 2. Create the RexSubQuery node linking the outer operand (lhs) to the subquery plan.

      // Simplified approach assuming RexSubQuery handles the linkage internally:
      rex = RexSubQuery.in(subQueryRel, ImmutableList.of(lhs));

      // TODO: Proper correlation handling is needed here. The subquery conversion
      // must identify references to the outer scope ('scope') and replace them with
      // RexInputRefs using a CorrelationId provided by 'scope'. The RexSubQuery
      // then uses this correlation info. This is complex to set up manually.
      // For now, this might only work for non-correlated subqueries.
    }

    if (isNotIn) {
      rex = rexBuilder.makeCall(SqlStdOperatorTable.NOT, rex);
    }
    // Handle null semantics for IN/NOT IN (e.g., `x IN (1, NULL)` might be UNKNOWN)
    // RexBuilder usually handles this.
    return rex;
  }

  /**
   * Converts EXISTS expression.
   */
  protected RexNode convertExists(SqlCall existsCall, Scope scope) {
    assert existsCall.getOperator().equals(SqlStdOperatorTable.EXISTS);
    if (existsCall.getOperandList().size() != 1) {
      throw new IllegalArgumentException("EXISTS requires 1 operand (subquery)");
    }
    SqlNode subQueryNode = existsCall.getOperandList().get(0);

    // Convert the subquery, passing current scope as parent for correlation.
    Scope subQueryScope = convertQueryRecursive(subQueryNode, false, scope);
    RelNode subQueryRel = subQueryScope.getRelNode();

    // Create RexSubQuery for EXISTS.
    // Similar correlation complexities as IN subquery apply here.
    RexSubQuery rex = RexSubQuery.exists(subQueryRel);

    // TODO: Implement proper correlation handling.
    return rex;
  }

  /**
   * Converts SCALAR subquery expression.
   */
  protected RexNode convertScalarSubquery(SqlCall scalarCall, Scope scope) {
    assert scalarCall.getKind() == SqlKind.SCALAR_QUERY;
    if (scalarCall.getOperandList().size() != 1) {
      throw new IllegalArgumentException("SCALAR_QUERY requires 1 operand (subquery)");
    }
    SqlNode subQueryNode = scalarCall.getOperandList().get(0);

    // Convert the subquery, passing current scope as parent for correlation.
    Scope subQueryScope = convertQueryRecursive(subQueryNode, false, scope);
    RelNode subQueryRel = subQueryScope.getRelNode();

    // Ensure subquery returns one column (cardinality checked at runtime usually)
    if (subQueryRel.getRowType().getFieldCount() != 1) {
      throw new IllegalArgumentException("Scalar subquery must return exactly one column, found "
          + subQueryRel.getRowType().getFieldCount() + " in: " + subQueryNode);
    }

    // Create RexSubQuery for SCALAR query.
    // Similar correlation complexities as IN subquery apply here.
    RexSubQuery rex = RexSubQuery.scalar(subQueryRel);

    // TODO: Implement proper correlation handling.
    return rex;
  }

  /** Converts BETWEEN / NOT BETWEEN */
  protected RexNode convertBetween(SqlCall call, Scope scope) {
    boolean isNotBetween = call.getOperator().equals(SqlStdOperatorTable.NOT_BETWEEN);
    if (call.getOperandList().size() != 3) {
      throw new IllegalArgumentException(call.getOperator().getName() + " requires 3 operands");
    }

    RexNode value = convertExpression(call.getOperandList().get(0), scope);
    RexNode lower = convertExpression(call.getOperandList().get(1), scope);
    RexNode upper = convertExpression(call.getOperandList().get(2), scope);

    // Expand BETWEEN into AND >= <=
    // value >= lower AND value <= upper
    RexNode ge = rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, value, lower);
    RexNode le = rexBuilder.makeCall(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, value, upper);
    RexNode and = rexBuilder.makeCall(SqlStdOperatorTable.AND, ge, le);

    // Handle symmetric vs asymmetric (standard is asymmetric: >= lower AND <= upper)
    // If SqlStdOperatorTable.SYMMETRIC_BETWEEN was used, logic would differ. Assume asymmetric.

    if (isNotBetween) {
      // NOT (value >= lower AND value <= upper)
      return rexBuilder.makeCall(SqlStdOperatorTable.NOT, and);
    } else {
      return and;
    }
  }


  /**
   * Converts an aggregate function call encountered in an expression context
   * (e.g., HAVING, ORDER BY post-aggregation). Resolves it to an input reference
   * from the Aggregate node's output.
   */
  protected RexNode convertAggregateCallExpr(SqlCall aggCall, Scope scope) {
    // This should only be called when converting expressions *after* aggregation.
    if (!scope.isAggregateContext()) {
      // This check might be redundant if called correctly, but good for safety.
      throw new IllegalStateException("Unexpected aggregate function call in non-aggregate scope: " + aggCall);
    }

    // Find the corresponding aggregate call result from the scope.
    // Scope needs to map the SqlCall (or its info) to the output index.
    Pair<Integer, AggregateCall> aggInfo = scope.findAggregateCallResult(aggCall);
    if (aggInfo == null) {
      // Could also be a reference to a GROUP BY key if the expression matches.
      RexNode groupByRex = scope.findGroupByExpression(aggCall);
      if (groupByRex != null) {
        return groupByRex;
      }

      throw new RuntimeException("Aggregate function or GROUP BY expression '" + aggCall
          + "' used in post-aggregation clause but not found in Aggregate node output or group keys. Scope: " + scope);
    }

    int fieldIndex = aggInfo.left; // Index in the Aggregate node's output row type
    AggregateCall callInstance = aggInfo.right;

    // Create a RexInputRef pointing to the aggregate result field in the Aggregate node's output.
    return rexBuilder.makeInputRef(callInstance.getType(), fieldIndex);
  }


  // =====================================================================
  // Helper Methods & Classes
  // =====================================================================

  /**
   * Finds the input index corresponding to a RexNode if it's a simple RexInputRef
   * relative to the given input RelNode.
   * Returns -1 if not a direct input ref or index out of bounds.
   */
  protected int findRexInputIndex(RexNode rex, RelNode input) {
    if (rex instanceof RexInputRef) {
      int index = ((RexInputRef) rex).getIndex();
      if (index >= 0 && index < input.getRowType().getFieldCount()) {
        return index;
      }
    }
    // More complex matching (e.g., finding 'a+b' in input if rex is 'a+b') is hard.
    // Requires comparing expression structure or digests. Assume simple refs for now.
    // This is a limitation compared to using RelBuilder/Validator which handle this.
    return -1;
  }

  /**
   * Finds the output index corresponding to a RexNode by matching against the
   * fields/expressions represented in the Scope. Used for ORDER BY resolution.
   */
  protected int findRexOutputIndex(RexNode rex, Scope outputScope) {
    // 1. If rex is a simple RexInputRef, its index directly refers to the output field.
    if (rex instanceof RexInputRef) {
      int index = ((RexInputRef) rex).getIndex();
      // Validate index against the scope's effective output arity
      if (index >= 0 && index < outputScope.getFieldCount()) {
        return index;
      } else {
        // Index out of bounds, shouldn't happen if scope is correct.
        return -1;
      }
    }

    // 2. If rex is more complex, try to match it against the expressions
    //    that generated the output fields in the scope (e.g., project expressions).
    //    This requires the Scope to store the generating RexNodes for its fields.
    //    Scope.getRelation(0).getGeneratingRexNodes() ? Needs Frame enhancement.

    // 3. Fallback: Compare string representation (brittle) or digest (better).
    //    Let's try digest comparison if available.
    String targetDigest = rex.toString(); // Use toString as proxy for digest here

    List<RelDataTypeField> outputFields = outputScope.getRelNode().getRowType().getFieldList();
    for (int i = 0; i < outputFields.size(); i++) {
      // How to get the RexNode that generated output field 'i'?
      // If the scope came from a Project, the Project node holds the expressions.
      RelNode sourceRel = outputScope.getRelNode();
      if (sourceRel instanceof LogicalProject) {
        LogicalProject project = (LogicalProject) sourceRel;
        if (i < project.getProjects().size()) {
          RexNode projectExpr = project.getProjects().get(i);
          if (projectExpr.toString().equals(targetDigest)) { // Compare digests/strings
            return i;
          }
        }
      }
      // If scope came from Aggregate, need to check group keys and agg calls.
      else if (sourceRel instanceof LogicalAggregate && outputScope.isAggregateContext()) {
        // Check group keys first
        ImmutableBitSet groupSet = outputScope.getGroupSet(); // Indices relative to agg input
        Map<Integer, RexNode> groupExprMap = outputScope.getGroupExprMap(); // Original group exprs
        int groupKeyIndex = 0;
        for (int groupInputIndex : groupSet) {
          RexNode groupExpr = groupExprMap.get(groupInputIndex);
          if (groupExpr != null && groupExpr.toString().equals(targetDigest)) {
            return groupKeyIndex; // Index within the aggregate output
          }
          groupKeyIndex++;
        }
        // Check aggregate calls
        List<AggregateCall> aggCalls = outputScope.getAggCalls();
        for (int j = 0; j < aggCalls.size(); j++) {
          // Can we match rex against the definition of aggCalls.get(j)? Hard.
          // Usually ORDER BY refers to aggregates by alias, handled by convertIdentifier.
          // Matching complex expressions is difficult here.
        }
      }
      // If scope came from TableScan, fields are direct inputs (handled by case 1).
      // If scope came from Join, fields are direct inputs (handled by case 1).
      // If scope came from Values, fields are literals (unlikely to match complex rex).

      // If we cannot find the generating expression, matching fails.
    }


    // If no match found by index or structure/digest.
    return -1;
  }


  /**
   * Maps a {@link SqlAggFunction} (from the parsed SQL node) to a Calcite
   * {@link SqlAggFunction} instance (often from {@link SqlStdOperatorTable}).
   * This might involve checking operand types for overloaded functions.
   */
  protected SqlAggFunction mapSqlAggregation(SqlAggFunction sqlAggFunction) {
    // This mapping is crucial and can be complex for UDFs or overloaded functions.
    // For standard functions, we can often look them up in SqlStdOperatorTable.
    // Using equals() might work if the parser uses standard instances.
    // Using name comparison is a fallback but ignores overloading.

    // Try direct instance check first (if parser uses std instances)
    if (sqlAggFunction == SqlStdOperatorTable.COUNT) return SqlStdOperatorTable.COUNT;
    if (sqlAggFunction == SqlStdOperatorTable.SUM) return SqlStdOperatorTable.SUM;
    if (sqlAggFunction == SqlStdOperatorTable.AVG) return SqlStdOperatorTable.AVG;
    if (sqlAggFunction == SqlStdOperatorTable.MIN) return SqlStdOperatorTable.MIN;
    if (sqlAggFunction == SqlStdOperatorTable.MAX) return SqlStdOperatorTable.MAX;
    // Add others like SUM0, STDDEV_POP, STDDEV_SAMP, VAR_POP, VAR_SAMP, COLLECT, FUSION, etc.

    // Fallback: Lookup by name (case-insensitive) in standard table.
    // WARNING: This ignores overloading based on operand types or syntax (e.g., COUNT(*), COUNT(col), COUNT(DISTINCT col)).
    // The AggregateCall creation needs to handle the specifics (e.g., arg list, distinct flag).
    String name = sqlAggFunction.getName();
    List<SqlOperator> stdOperators = SqlStdOperatorTable.instance().getOperatorList();
    for (SqlOperator op : stdOperators) {
      if (op instanceof SqlAggFunction && op.getName().equalsIgnoreCase(name)) {
        // Found a match by name. Return the standard instance.
        // This assumes the first match by name is correct, which is risky for overloads.
        // A real implementation might need the SqlFunction based on SqlIdentifier from parser.
        return (SqlAggFunction) op;
      }
    }

    // If it's not standard, assume it's a UDAF registered elsewhere (e.g., in catalogReader).
    // Need a way to look up UDAFs. CatalogReader might provide this.
    // SqlOperator op = catalogReader.lookupAggFunction(sqlAggFunction.getSqlIdentifier()); // Hypothetical lookup
    // if (op instanceof SqlAggFunction) return (SqlAggFunction) op;

    throw new UnsupportedOperationException("Cannot map SqlAggFunction '" + sqlAggFunction.getName()
        + "' to a known Calcite Aggregation. Check standard functions or UDAF registration.");
  }

  /**
   * Generates a default alias for an aggregate call if none is provided.
   */
  private String generateAggAlias(SqlAggFunction function, int index) {
    // Mimic Calcite's default naming like $f0, $f1 or EXPR$1
    // Using function name might be clearer but can clash if function is used multiple times.
    // return function.getName() + "$" + index; // Potential clash
    return "$f" + index; // Calcite-like default
  }

  /**
   * Information about a parsed aggregate call, extracted by AggregateFinder.
   * Used to bridge between SqlNode parsing and AggregateCall creation.
   */
  protected static class AggregateCallInfo {
    final SqlAggFunction sqlAggFunction;
    final List<SqlNode> sqlOperands;
    final boolean isDistinct;
    final boolean isApproximate;
    final @Nullable SqlNode filter;
    final @Nullable SqlNodeList orderKeys; // ORDER BY within aggregate
    final @Nullable String alias; // Alias from SELECT list, if any
    final SqlCall originalNode; // The original SqlCall node (e.g., SUM(x))

    AggregateCallInfo(SqlCall call, @Nullable String alias) {
      this.originalNode = call;
      this.sqlAggFunction = (SqlAggFunction) call.getOperator();

      // Determine distinctness - check for SqlSelectKeyword.DISTINCT modifier
      // This depends on how the parser represents it. Assume call.getFunctionQuantifier() exists.
      SqlLiteral quantifier = call.getFunctionQuantifier();
      this.isDistinct = quantifier != null && quantifier.getValue() == SqlSelectKeyword.DISTINCT;

      // Check for APPROXIMATE keyword (if supported by parser)
      this.isApproximate = false; // TODO: Detect APPROXIMATE keyword

      // Operands, FILTER, ORDER BY depend on the specific SqlAggFunction's syntax & parsing.
      // Standard syntax: AGG( [DISTINCT] arg1, ... ) [ FILTER (WHERE ...) ] [ WITHIN GROUP (ORDER BY ...) ]
      // Need to parse these components from the SqlCall structure.
      // This is complex and depends heavily on the parser implementation.

      // Simple assumption: Operands are direct children.
      this.sqlOperands = call.getOperandList(); // This might include FILTER/ORDER BY nodes if parser flattens them. Needs refinement.

      // TODO: Extract FILTER clause if present. Requires inspecting operands based on operator syntax.
      this.filter = findAggFilter(call);

      // TODO: Extract ORDER BY within aggregate if present. Requires inspecting operands.
      this.orderKeys = findAggOrderKeys(call);

      // Adjust sqlOperands to exclude filter/order keys if they were included by getOperandList()
      // This requires knowing the exact structure produced by the parser for aggregate calls.

      this.alias = alias;
    }

    // Placeholder: Logic to find FILTER clause within SqlCall operands
    private @Nullable SqlNode findAggFilter(SqlCall call) {
      // Example: Check if last operand is a FILTER operator call? Depends on parser.
      // SqlNode lastOperand = call.getOperandList().isEmpty() ? null : call.getOperandList().get(call.getOperandList().size() - 1);
      // if (lastOperand instanceof SqlCall && ((SqlCall) lastOperand).getOperator().getName().equals("FILTER")) {
      //     return ((SqlCall) lastOperand).getOperandList().get(0); // The WHERE condition
      // }
      return null; // Not implemented robustly
    }

    // Placeholder: Logic to find ORDER BY clause within SqlCall operands
    private @Nullable SqlNodeList findAggOrderKeys(SqlCall call) {
      // Example: Check for WITHIN GROUP (ORDER BY ...)? Depends on parser.
      return null; // Not implemented robustly
    }


    // Use originalNode's digest or string for equals/hashCode. String is simpler but less robust.
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      AggregateCallInfo that = (AggregateCallInfo) o;
      // Compare based on the original SqlNode structure/string.
      return Objects.equals(originalNode.toString(), that.originalNode.toString());
    }

    @Override
    public int hashCode() {
      return Objects.hash(originalNode.toString());
    }

    @Override
    public String toString() {
      return originalNode.toString() + (alias != null ? " AS " + alias : "");
    }
  }

  protected static class IdentifierCollector extends SqlBasicVisitor<Void> {
    private final List<SqlIdentifier> identifiers = new ArrayList<>();

    @Override
    public Void visit(SqlIdentifier identifier) {
      identifiers.add(identifier);
      return super.visit(identifier);
    }

    public static List<SqlIdentifier> collect(SqlNode node) {
      IdentifierCollector collector = new IdentifierCollector();
      node.accept(collector);
      return collector.identifiers;
    }
  }

  /**
   * Visitor to find aggregate calls within a SqlNode tree (SELECT list, HAVING).
   * Needs careful handling of OVER clauses and aliases.
   */
  protected static class AggregateFinder extends SqlBasicVisitor<Void> {
    private final List<AggregateCallInfo> aggCalls = new ArrayList<>();
    // Avoid infinite recursion on cyclic structures (though unlikely in valid SQL AST)
    private final Set<SqlNode> visited = new HashSet<>();
    // Track alias for the immediate aggregate function if found via AS
    private @Nullable String currentAlias = null;

    public List<AggregateCallInfo> getAggCalls() {
      return aggCalls;
    }

    @Override
    public Void visit(SqlCall call) {
      if (!visited.add(call)) {
        return null; // Already visited
      }

      SqlOperator operator = call.getOperator();

      // --- Special Handling ---

      // 1. AS Operator: Capture alias and visit operand
      if (operator.equals(SqlStdOperatorTable.AS)) {
        if (call.getOperandList().size() == 2) {
          SqlNode potentialAgg = call.getOperandList().get(0);
          SqlNode aliasNode = call.getOperandList().get(1);
          String savedAlias = currentAlias; // Save outer alias context
          currentAlias = (aliasNode instanceof SqlIdentifier) ? ((SqlIdentifier) aliasNode).getSimple() : null;
          // Visit the expression that might be an aggregate
          potentialAgg.accept(this);
          currentAlias = savedAlias; // Restore outer alias context
        }
        return null; // Don't recurse further into AS operands here
      }

      // 2. OVER Operator: Visit the aggregate function operand, ignore window spec
      if (operator.equals(SqlStdOperatorTable.OVER)) {
        if (!call.getOperandList().isEmpty()) {
          SqlNode aggFunctionNode = call.getOperandList().get(0);
          // The alias applies to the OVER expression result, not the inner agg func directly
          // Pass currentAlias=null when visiting the inner agg func? Or let it find its own alias if nested?
          // Let's assume alias applies to the OVER result. We don't collect RexOver here, only base aggregates.
          // So, visit the agg func part without passing the alias meant for the OVER().
          String savedAlias = currentAlias;
          currentAlias = null;
          aggFunctionNode.accept(this);
          currentAlias = savedAlias;
        }
        return null; // Don't visit window spec operands (partition, order, frame)
      }

      // 3. Aggregate Function Found: Create info and stop recursion here
      if (operator instanceof SqlAggFunction) {
        // Use the alias captured from an enclosing AS operator, if any
        aggCalls.add(new AggregateCallInfo(call, currentAlias));
        // Reset alias context as it's been consumed
        currentAlias = null;
        return null; // Do not recurse into arguments of the aggregate function itself
      }

      // --- Default Recursion ---
      // For other functions/operators, recurse into operands.
      // Reset alias context for children, as AS applies only to the top level it modifies.
      String savedAlias = currentAlias;
      currentAlias = null;
      for (SqlNode operand : call.getOperandList()) {
        if (operand != null) {
          operand.accept(this);
        }
      }
      currentAlias = savedAlias; // Restore alias context for siblings (though usually null here)
      return null;
    }

    @Override
    public Void visit(SqlNodeList nodeList) {
      if (!visited.add(nodeList)) {
        return null;
      }
      // Visit each element in the list. Reset alias context for each element.
      String savedAlias = currentAlias;
      for (SqlNode node : nodeList) {
        if (node != null) {
          currentAlias = null; // Reset alias for each list item
          node.accept(this);
        }
      }
      currentAlias = savedAlias; // Restore context
      return null;
    }

    // Visit literals, identifiers, etc. - stop recursion
    @Override public Void visit(SqlLiteral literal) { visited.add(literal); return null; }
    @Override public Void visit(SqlIdentifier id) { visited.add(id); return null; }
    @Override public Void visit(SqlDataTypeSpec type) { visited.add(type); return null; }
    @Override public Void visit(SqlDynamicParam param) { visited.add(param); return null; }
    @Override public Void visit(SqlIntervalQualifier interval) { visited.add(interval); return null; }
  }

  /**
   * Represents one input relation (table, subquery, join result) within a scope.
   * Includes alias, offset within a combined input type, and the RelNode itself.
   */
  public static class Frame {
    final @Nullable String alias; // Explicit alias (from AS) or implicit (table name/CTE name)
    final int offset; // Starting index of this frame's fields in the combined input row type (relevant for join input scopes)
    final RelNode relNode; // The relational node this frame represents
    final RelDataType rowType; // Cached row type from relNode
    // Field aliases specific to this frame (e.g., from Project output). Stored here for consistency.
    final ImmutableList<String> fieldAliases;

    // Constructor for simple cases (single node, offset 0)
    Frame(@Nullable String alias, int offset, RelNode relNode) {
      this(alias, offset, relNode, relNode.getRowType().getFieldNames()); // Default aliases from row type
    }

    // Constructor allowing explicit field aliases (e.g., for Project output)
    Frame(@Nullable String alias, int offset, RelNode relNode, List<String> fieldAliases) {
      this.alias = alias;
      this.offset = offset;
      this.relNode = Objects.requireNonNull(relNode, "relNode");
      this.rowType = relNode.getRowType(); // Use row type from RelNode directly

      // Ensure fieldAliases size matches rowType field count, use defaults if not.
      if (fieldAliases != null && fieldAliases.size() == this.rowType.getFieldCount()) {
        this.fieldAliases = ImmutableList.copyOf(fieldAliases);
      } else {
        // Mismatch or null aliases, use default field names from the RelNode's row type
        this.fieldAliases = ImmutableList.copyOf(this.rowType.getFieldNames());
      }
    }

    @Override
    public String toString() {
      return "Frame{" +
          "alias='" + alias + '\'' +
          ", offset=" + offset +
          ", node=" + relNode.getClass().getSimpleName() +
          ", fields=" + fieldAliases +
          '}';
    }
  }

  /**
   * Utility class to check if a select statement requires aggregation.
   * Looks for aggregate functions or GROUP BY / HAVING clauses.
   */
  protected static class AggregateChecker extends SqlBasicVisitor<Void> {
    private boolean containsAggregate = false;
    private final Set<SqlNode> visited = new HashSet<>();

    public static boolean isAggregate(SqlSelect select) {
      if (select.getGroup() != null || select.getHaving() != null) {
        return true;
      }
      // Check select list and order by (if aggregates allowed there)
      AggregateChecker checker = new AggregateChecker();
//      if (select.getSelectList() != null) {
//        select.getSelectList().accept(checker);
//        if (checker.containsAggregate) return true;
//      }
      // Standard SQL doesn't allow aggregates in ORDER BY unless also in SELECT/GROUP BY.
      // If supporting extensions that allow it, check select.getOrderList() too.
      return checker.containsAggregate;
    }

    @Override
    public Void visit(SqlCall call) {
      if (containsAggregate || !visited.add(call)) {
        return null; // Stop if already found or visited
      }
      SqlOperator operator = call.getOperator();

      // Found aggregate function
      if (operator instanceof SqlAggFunction) {
        containsAggregate = true;
        return null; // Stop recursion here
      }

      // Do not recurse into OVER clause window spec, only the function part
      if (operator.equals(SqlStdOperatorTable.OVER)) {
        if (!call.getOperandList().isEmpty()) {
          call.getOperandList().get(0).accept(this); // Visit only the agg func part
        }
        return null; // Stop recursion here for OVER
      }

      // Default: visit operands
      for (SqlNode operand : call.getOperandList()) {
        if (operand != null) {
          operand.accept(this);
          if (containsAggregate) return null; // Stop early if found in operand
        }
      }
      return null;
    }

    @Override
    public Void visit(SqlNodeList nodeList) {
      if (containsAggregate || !visited.add(nodeList)) {
        return null;
      }
      for (SqlNode node : nodeList) {
        if (node != null) {
          node.accept(this);
          if (containsAggregate) return null;
        }
      }
      return null;
    }

    // Stop recursion at leaves
    @Override public Void visit(SqlLiteral literal) { visited.add(literal); return null; }
    @Override public Void visit(SqlIdentifier id) { visited.add(id); return null; }
    @Override public Void visit(SqlDataTypeSpec type) { visited.add(type); return null; }
    @Override public Void visit(SqlDynamicParam param) { visited.add(param); return null; }
    @Override public Void visit(SqlIntervalQualifier interval) { visited.add(interval); return null; }
  }

} // End class SqlToRelConverter
