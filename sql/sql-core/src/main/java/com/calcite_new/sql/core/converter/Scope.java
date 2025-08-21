package com.calcite_new.sql.core.converter;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.CorrelationId;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Pair;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages the scope for identifier resolution during conversion.
 * Tracks input relations (frames), CTEs, and context for aggregate/correlation.
 * Each Scope primarily represents the output of a specific RelNode.
 */
public class Scope {
  private final @Nullable Scope parent;
  // Relations (frames) providing fields in this scope. Usually one after Project/Agg/Scan. Multiple during join condition resolution.
  private final ImmutableList<CustomSqlToRelConverter.Frame> relations;
  // CTEs available in this scope (inherited from parent, potentially added to in WITH)
  private final @Nullable Map<String, RelNode> cteMap;
  // Alias for the primary relation represented by this scope (if any)
  private final @Nullable String alias;

  // --- Context for Aggregate Resolution ---
  private final boolean isAggregateContext; // True if scope represents output of Aggregate/Having
  private final @Nullable RelNode aggregateNode; // The LogicalAggregate node (if isAggregateContext)
  private final @Nullable ImmutableBitSet groupSet; // Indices of group keys in Aggregate *input*
  private final @Nullable Map<Integer, RexNode> groupExprMap; // Map group key index (in input) -> original RexNode
  private final @Nullable List<AggregateCall> aggCalls; // Aggregate calls in the Aggregate node
  private final @Nullable Map<CustomSqlToRelConverter.AggregateCallInfo, Integer> aggCallOutputIndexMap; // Map SqlCall info -> index in aggCalls list

  // --- Context for Correlation ---
  // TODO: Add fields for correlation variables if implementing correlated subqueries
  // private final @Nullable CorrelationId correlationId; // ID if this scope is correlated from parent
  // private final @Nullable RelDataType correlationType; // Row type of the correlation source

  // Private constructor, use factory methods
  protected Scope(@Nullable Scope parent,
                ImmutableList<CustomSqlToRelConverter.Frame> relations,
                @Nullable Map<String, RelNode> cteMap,
                @Nullable String alias,
                boolean isAggregateContext,
                @Nullable RelNode aggregateNode,
                @Nullable ImmutableBitSet groupSet,
                @Nullable Map<Integer, RexNode> groupExprMap,
                @Nullable List<AggregateCall> aggCalls,
                @Nullable Map<CustomSqlToRelConverter.AggregateCallInfo, Integer> aggCallOutputIndexMap) {
    this.parent = parent;
    this.relations = Objects.requireNonNull(relations, "relations");
    this.cteMap = cteMap; // Can be null if root or not in WITH
    this.alias = alias;
    this.isAggregateContext = isAggregateContext;
    this.aggregateNode = aggregateNode;
    this.groupSet = groupSet;
    this.groupExprMap = groupExprMap;
    this.aggCalls = aggCalls;
    this.aggCallOutputIndexMap = aggCallOutputIndexMap;

    // Basic validation: If aggregate context, related fields should be non-null
    if (isAggregateContext && (aggregateNode == null || groupSet == null || groupExprMap == null || aggCalls == null || aggCallOutputIndexMap == null)) {
      // Allow creation with nulls initially, maybe? Or enforce here?
      // Let's allow it but be cautious. A fully constructed agg scope should have these.
      // Consider adding checks in methods using these fields.
    }
  }

  // --- Factory Methods ---

  public static Scope createRoot() {
    return new Scope(null, ImmutableList.of(), null, null, false, null, null, null, null, null);
  }

  /** Scope for processing WITH clause definitions. Inherits CTEs, allows adding new ones. */
  public static Scope createWithScope(Scope parent) {
    // Inherit parent's CTEs, but allow defining new ones at this level
    Map<String, RelNode> parentCtes = (parent != null) ? parent.cteMap : null;
    Map<String, RelNode> newCteMap = (parentCtes != null) ? new HashMap<>(parentCtes) : new HashMap<>();
    // Relations are typically empty until the WITH body's FROM is processed.
    // Alias is not applicable here.
    return new Scope(parent, ImmutableList.of(), newCteMap, null, false, null, null, null, null, null);
  }

  /** Scope representing a single RelNode source (Scan, Values, Subquery result) with an alias. */
  public static Scope createScopeForRelNode(Scope parent, RelNode relNode, @Nullable String alias) {
    CustomSqlToRelConverter.Frame frame = new CustomSqlToRelConverter.Frame(alias, 0, relNode); // Single frame, offset 0
    return new Scope(parent, ImmutableList.of(frame), parent != null ? parent.cteMap : null, alias,
        false, null, null, null, null, null);
  }

  /** Creates a scope identical to an existing one but with a new alias. */
  public static Scope createScopeWithAlias(Scope parent, RelNode relNode, String alias) {
    // Assumes the input relNode corresponds to a single conceptual source.
    CustomSqlToRelConverter.Frame frame = new CustomSqlToRelConverter.Frame(alias, 0, relNode);
    return new Scope(parent, ImmutableList.of(frame), parent != null ? parent.cteMap : null, alias,
        false, null, null, null, null, null);
  }


  /** Scope for resolving JOIN conditions. Contains frames for both left and right inputs. */
  public static Scope createJoinInputScope(Scope parent, Scope leftScope, Scope rightScope) {
    RelNode leftRel = leftScope.getRelNode();
//    RelNode rightRel = rightScope.getRelNode();
    int leftFieldCount = leftRel.getRowType().getFieldCount();

    // Create frames preserving original aliases and calculating offsets
    List<CustomSqlToRelConverter.Frame> leftFrames = getNewFrames(leftScope, 0);
    List<CustomSqlToRelConverter.Frame> rightFrames = getNewFrames(rightScope, leftFieldCount);
    ImmutableList<CustomSqlToRelConverter.Frame> childFrames = ImmutableList.<CustomSqlToRelConverter.Frame>builder()
        .addAll(leftFrames)
        .addAll(rightFrames)
        .build();
//    SqlToRelConverter.Frame leftFrame = new SqlToRelConverter.Frame(leftScope.getAlias(), 0, leftRel);
//    SqlToRelConverter.Frame rightFrame = new SqlToRelConverter.Frame(rightScope.getAlias(), leftFieldCount, rightRel);

    // Inherit CTE map from parent
    Map<String, RelNode> cteMap = (parent != null) ? parent.cteMap : null;

    return new Scope(parent, childFrames, cteMap, null, // No single alias for join input
        false, null, null, null, null, null);
  }

  private static List<CustomSqlToRelConverter.Frame> getNewFrames(Scope scope, int offset) {
    // Check if it's a JOIN scope
    // TODO not a right way to check join scope
    List<CustomSqlToRelConverter.Frame> frames;
    if (scope.relations.size() > 1) {
      frames = scope.relations.stream()
          .map(f -> new CustomSqlToRelConverter.Frame(f.alias, f.offset + offset, f.relNode))
          .toList();
    } else {
      frames = List.of(new CustomSqlToRelConverter.Frame(scope.alias, offset, scope.getRelNode()));
    }
    return frames;
  }

  /** Scope representing the output of a JOIN. Contains frames reflecting the joined structure. */
  public static Scope createJoinOutputScope(Scope parent, RelNode joinRel, Scope leftScope, Scope rightScope) {
    // The output scope conceptually has fields from left then right.
    // We can represent this with multiple frames (like input scope) or a single frame
    // wrapping the joinRel. Single frame is simpler for subsequent steps.
    int leftFieldCount = leftScope.getRelNode().getRowType().getFieldCount();

    // Option 1: Multiple frames (mirrors input structure) - useful if needing left/right distinction later
//    SqlToRelConverter.Frame leftFrame = new SqlToRelConverter.Frame(leftScope.getAlias(), 0, leftScope.getRelNode()); // Offset 0, but wraps the whole joinRel
//    SqlToRelConverter.Frame rightFrame = new SqlToRelConverter.Frame(rightScope.getAlias(), leftFieldCount, rightScope.getRelNode()); // Offset leftCount, wraps whole joinRel
    List<CustomSqlToRelConverter.Frame> leftFrames = getNewFrames(leftScope, 0);
    List<CustomSqlToRelConverter.Frame> rightFrames = getNewFrames(rightScope, leftFieldCount);
    ImmutableList<CustomSqlToRelConverter.Frame> childFrames = ImmutableList.<CustomSqlToRelConverter.Frame>builder()
        .addAll(leftFrames)
        .addAll(rightFrames)
        .build();

//      // Option 2: Single frame wrapping the joinRel. Simpler.
//      // The alias for the join result itself is usually null unless aliased via AS.
//      Frame joinFrame = new Frame(null, 0, joinRel); // Alias null, offset 0

    Map<String, RelNode> cteMap = (parent != null) ? parent.cteMap : null;
    return new JoinScope(parent, childFrames, joinRel, cteMap);
    // Note: If NATURAL or USING requires projection, this scope might be intermediate,
    // followed by a project scope.
  }

  /** Scope representing the output of a Project. Single frame with specified aliases. */
  public static Scope createProjectScope(Scope parent, RelNode projectNode, List<String> aliases) {
    // Projection scope replaces the previous relations with a single one.
    CustomSqlToRelConverter.Frame relation = new CustomSqlToRelConverter.Frame(null, 0, projectNode, aliases); // Project itself usually has no alias
    Map<String, RelNode> cteMap = (parent != null) ? parent.cteMap : null;
    return new Scope(parent, ImmutableList.of(relation), cteMap, null,
        false, null, null, null, null, null);
  }

  /** Scope representing the output of LogicalAggregate (or subsequent Filter for HAVING). */
  public static Scope createAggregateOutputScope(Scope parent, RelNode aggregateOrFilterNode,
                                                 ImmutableBitSet groupSet, Map<Integer, RexNode> groupExprMap,
                                                 List<AggregateCall> aggCalls, Map<CustomSqlToRelConverter.AggregateCallInfo, Integer> aggCallOutputIndexMap) {
    // Scope representing the output of LogicalAggregate, used for HAVING/ORDER BY
    CustomSqlToRelConverter.Frame relation = new CustomSqlToRelConverter.Frame(null, 0, aggregateOrFilterNode); // Output fields are 0..N-1
    Map<String, RelNode> cteMap = (parent != null) ? parent.cteMap : null;
    return new Scope(parent, ImmutableList.of(relation), cteMap, null, true, // isAggregateContext = true
        aggregateOrFilterNode, // Store the node itself for reference
        groupSet, groupExprMap, aggCalls, aggCallOutputIndexMap);
  }


  // --- Accessor Methods ---

  /** Returns the primary RelNode represented by this scope. */
  public RelNode getRelNode() {
    if (relations.isEmpty()) {
      // Should happen only for root scope before conversion or intermediate WITH scope?
      // Or FROM-less select? Handle FROM-less case.
      VolcanoPlanner planner = new VolcanoPlanner();
      RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
      RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
      if (parent == null && cteMap == null) return LogicalValues.createOneRow(cluster); // Hack for root/empty
      throw new IllegalStateException("Scope has no relations, cannot get RelNode. Scope: " + this);
    }
    if (relations.size() == 1) {
      return relations.get(0).relNode;
    }
    // If multiple relations (e.g., join input scope), which node to return?
    // This method assumes the scope represents *one* logical output node.
    // Join *input* scope should perhaps not expose a single RelNode this way.
    // Let's assume callers only call getRelNode on scopes representing a single output node.
    throw new IllegalStateException("Scope has multiple relations (" + relations.size() + "), ambiguous to get single RelNode. Scope: " + this);
    // Alternative: Return the node associated with the *last* frame? Or the *first*? Unclear.
    // Best practice: Ensure scopes passed around represent single logical outputs.
  }

  /** Returns the parent scope, or null if root. */
  public @Nullable Scope getParent() {
    return parent;
  }

  /** Returns the alias of the primary relation in this scope, if any. */
  public @Nullable String getAlias() {
    // If there's an explicit alias for the scope itself (e.g., from AS)
    if (this.alias != null) return this.alias;
    // If single relation, return its alias
    if (relations.size() == 1) return relations.get(0).alias;
    // Otherwise, no single alias applies
    return null;
  }

  /** Returns the list of frames (relations) in this scope. */
  public ImmutableList<CustomSqlToRelConverter.Frame> getRelations() {
    return relations;
  }

  /** Returns the number of fields in the output represented by this scope. */
  public int getFieldCount() {
    if (relations.isEmpty()) return 0;
    // Assumes scope represents a single logical output node.
    return getRelNode().getRowType().getFieldCount();
  }

  /** Returns the names of the fields in the output represented by this scope. */
  public List<String> getFieldNames() {
    if (relations.isEmpty()) return ImmutableList.of();
    // Assumes scope represents a single logical output node.
    return getRelNode().getRowType().getFieldNames();
  }


  // --- CTE Methods ---

  /** Adds a CTE definition to this scope's context. */
  public void addCte(String name, RelNode rel) {
    if (cteMap == null) {
      throw new IllegalStateException("Cannot add CTE to this scope type (cteMap is null)");
    }
    if (cteMap.containsKey(name)) {
      throw new IllegalArgumentException("Duplicate CTE name defined: " + name);
    }
    cteMap.put(name, rel);
  }

  /** Checks if a CTE name is defined in this scope or accessible parents. */
  public boolean isCteDefined(String name) {
    return findCte(name) != null;
  }

  /** Finds a CTE by name, searching current scope then parents recursively. */
  public @Nullable RelNode findCte(String name) {
    if (cteMap != null) {
      RelNode cte = cteMap.get(name);
      if (cte != null) {
        return cte;
      }
    }
    // Standard SQL: CTEs are generally visible only within their defining WITH clause
    // and subsequent sibling CTEs/main query body. They don't leak "up" to parent scopes.
    // However, nested WITH clauses can reference CTEs from outer WITH clauses.
    // So, search parent *if* parent also has a CTE map (meaning it's part of a WITH).
    if (parent != null && parent.cteMap != null) {
      return parent.findCte(name);
    }
    return null;
  }

  // --- Aggregate Context Methods ---

  public boolean isAggregateContext() {
    return isAggregateContext;
  }

  public @Nullable ImmutableBitSet getGroupSet() { return groupSet; }
  public @Nullable Map<Integer, RexNode> getGroupExprMap() { return groupExprMap; }
  public @Nullable List<AggregateCall> getAggCalls() { return aggCalls; }


  /**
   * Finds an aggregate call result by matching the SqlCall node.
   * Used for resolving aggregates in HAVING/ORDER BY.
   */
  public @Nullable Pair<Integer, AggregateCall> findAggregateCallResult(SqlCall sqlCall) {
    if (!isAggregateContext || aggCalls == null || aggCallOutputIndexMap == null || groupSet == null) {
      return null; // Not in the right context or context is incomplete
    }

    // Need to match sqlCall to one of the AggregateCallInfos used to create the Aggregate node.
    // Use the temporary AggregateCallInfo for matching (based on SqlNode structure/string).
    CustomSqlToRelConverter.AggregateCallInfo targetInfo = new CustomSqlToRelConverter.AggregateCallInfo(sqlCall, null); // Alias doesn't matter for matching call structure
    Integer aggCallIndex = aggCallOutputIndexMap.get(targetInfo); // Index within the aggCalls list (0-based)

    if (aggCallIndex != null) {
      // Found the aggregate call. Its index in the *output* row type of the Aggregate node
      // is groupSet.cardinality() + aggCallIndex.
      int outputIndex = groupSet.cardinality() + aggCallIndex;
      if (outputIndex < aggregateNode.getRowType().getFieldCount()) {
        return Pair.of(outputIndex, aggCalls.get(aggCallIndex));
      } else {
        // Should not happen if indices are correct
        throw new IllegalStateException("Calculated aggregate output index " + outputIndex + " is out of bounds for node " + aggregateNode);
      }
    }

    return null; // Not found
  }

  /**
   * Finds a GROUP BY expression result by matching the SqlCall node against original group expressions.
   * Used for resolving group keys referenced in HAVING/ORDER BY.
   */
  public @Nullable RexNode findGroupByExpression(SqlCall sqlCall) {
    if (!isAggregateContext || groupSet == null || groupExprMap == null) {
      return null;
    }

    // Convert the sqlCall to a RexNode using the *input* scope of the aggregate
    // This requires access to the scope *before* aggregation. Store it? Or assume parent?
    // Let's assume the parent scope is the input scope to the aggregation. Risky assumption.
    // A better way: Store the input scope within the AggregateOutputScope?

    // Workaround: Convert sqlCall using a temporary root scope if it's simple (e.g., identifier)
    // This is very limited. Proper solution needs the aggregate input scope.
    // Let's assume for now that references in HAVING/ORDER BY are simple identifiers or aliases
    // matching the output field names of the aggregate node.

    // Try matching sqlCall string against the groupExprMap values (original RexNodes)
    String targetDigest = sqlCall.toString(); // Use string as digest proxy
    int groupKeyIndex = 0;
    for (int inputIndex : groupSet) {
      RexNode originalGroupRex = groupExprMap.get(inputIndex);
      if (originalGroupRex != null && originalGroupRex.toString().equals(targetDigest)) {
        // Found match. Return a RexInputRef pointing to the group key in the *output* row type.
        RelDataTypeField outputField = aggregateNode.getRowType().getFieldList().get(groupKeyIndex);
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        return rexBuilder.makeInputRef(outputField.getType(), groupKeyIndex);
      }
      groupKeyIndex++;
    }

    // If not found by matching original expression, try resolving sqlCall as an identifier
    // against the output fields of the aggregate node.
//      if (sqlCall instanceof SqlIdentifier) {
//        Pair<Frame, RelDataTypeField> fieldInfo = findField((SqlIdentifier) sqlCall);
//        if (fieldInfo != null) {
//          // Check if this field corresponds to a group key position
//          int outputIndex = fieldInfo.left.offset + fieldInfo.right.getIndex();
//          if (outputIndex < groupSet.cardinality()) { // Check if it's within the group key part
//            RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
//            return rexBuilder.makeInputRef(fieldInfo.right.getType(), outputIndex);
//          }
//        }
//      }


    return null; // Not found or not identifiable as a group key reference
  }


  // --- Identifier Resolution Methods ---

  /** Find relation frame by alias (e.g., table alias in FROM). Searches current scope only. */
  public CustomSqlToRelConverter.Frame findRelationByAlias(String alias) {
    for (CustomSqlToRelConverter.Frame frame : relations) {
      if (frame.alias != null && frame.alias.equalsIgnoreCase(alias)) {
        return frame;
      }
    }
    return null;
  }

  /**
   * Find a field by identifier, searching current scope's frames.
   * Does NOT search parent scope (use findField searching parents separately if needed).
   */
  public @Nullable Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> findFieldInScope(SqlIdentifier id) {
    String simpleName = id.isSimple() ? id.getSimple() : id.names.get(id.names.size() - 1);
    // Qualifier is the first part if multipart, otherwise null.
    String qualifier = id.names.size() > 1 ? id.names.get(0) : null;

    CustomSqlToRelConverter.Frame foundFrame = null;
    RelDataTypeField foundField = null;
    int matchCount = 0;

    for (CustomSqlToRelConverter.Frame frame : relations) {
      boolean qualifierMatch = (qualifier == null) // Unqualified name might match any frame
          || (frame.alias != null && frame.alias.equalsIgnoreCase(qualifier)); // Qualified name must match frame alias

      if (qualifierMatch) {
        // Look for field name within this frame's row type (case-insensitive)
        RelDataTypeField field = frame.rowType.getField(simpleName, false, false);
        if (field != null) {
          // If qualifier was specified, this is our match.
          if (qualifier != null) {
            return Pair.of(frame, field);
          }
          // If qualifier was null, we need to check for ambiguity.
          matchCount++;
          foundFrame = frame;
          foundField = field;
          // If we find more than one match for an unqualified name, it's ambiguous.
          if (matchCount > 1) {
            throw new RuntimeException("Ambiguous identifier '" + simpleName + "' found in multiple relations in scope: " + relations.stream().map(f -> f.alias).collect(Collectors.toList()));
          }
        }
      }
    }

    // If exactly one match was found for an unqualified name
    if (matchCount == 1) {
      return Pair.of(foundFrame, foundField);
    }

    // No match found in this scope's frames
    return null;
  }

  /** Find a field by identifier, searching current scope then parents recursively. */
  public @Nullable Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> findField(SqlIdentifier id) {
    // Search current scope first
    Pair<CustomSqlToRelConverter.Frame, RelDataTypeField> fieldInfo = findFieldInScope(id);
    if (fieldInfo != null) {
      return fieldInfo;
    }
    // If not found, delegate to parent scope (for correlation or outer query access)
    if (parent != null) {
      return parent.findField(id);
    }
    // Not found anywhere up the chain
    return null;
  }

  // --- Correlation Methods (Placeholders) ---

  /** Finds a correlation variable by name. Requires Scope to manage CorrelationIds. */
  public @Nullable Pair<Scope, RelDataTypeField> findCorrelationVariable(SqlIdentifier id) {
    // TODO: Implement correlation lookup logic
    // 1. Check if 'id' refers to a correlation defined in a parent scope.
    // 2. Requires scopes to register CorrelationIds when entering subqueries.
    return null; // Placeholder
  }

  /** Gets the CorrelationId if this scope represents a correlated subquery input. */
  public @Nullable CorrelationId getCorrelationId() {
    // TODO: Return correlationId field if implemented
    return null; // Placeholder
  }

  /** Gets the row type of the correlation source. */
  public @Nullable RelDataType getCorrelationType() {
    // TODO: Return correlationType field if implemented
    return null; // Placeholder
  }

  /**
   * Creates a new CorrelationId for a subquery. Needs context.
   */
  public CorrelationId createCorrelationId(String alias) {
    // TODO: Implement correlation ID management, likely needs cluster access or a counter.
    // return cluster.createCorrel(); // Example if cluster provides it
    String idName = alias != null ? alias : "$cor1";
    return new CorrelationId(idName);
//    throw new UnsupportedOperationException("Correlation ID creation not implemented.");
  }


  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("Scope{");
    sb.append("alias=").append(alias);
    sb.append(", relations=").append(relations.stream().map(f -> f.alias + ":" + f.relNode.getClass().getSimpleName()).collect(Collectors.toList()));
    sb.append(", isAgg=").append(isAggregateContext);
    sb.append(", parent=").append(parent != null ? parent.hashCode() : "null"); // Use hashcode to avoid recursion
    sb.append(", cteKeys=").append(cteMap != null ? cteMap.keySet() : "N/A");
    sb.append('}');
    return sb.toString();
  }
}
