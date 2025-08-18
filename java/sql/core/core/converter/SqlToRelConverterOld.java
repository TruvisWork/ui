package com.calcite_new.sql.core.converter;

import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.ScannableTable;
import com.calcite_new.core.model.entity.RelationalEntity;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.google.common.collect.ImmutableList;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.prepare.RelOptTableImpl;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelFieldCollation;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.*;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.util.SqlBasicVisitor;
import org.apache.calcite.util.ImmutableBitSet;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class SqlToRelConverterOld {
  private final RelOptCluster cluster;
  private final EntityResolver resolver;
  private final SqlDialect dialect;
  private final List<String> defaultQualifiers;
  private final RexBuilder rexBuilder;
  private final RelDataTypeFactory typeFactory;
  private final Map<String, RelNode> cteMap;
  private final Map<String, RexCorrelVariable> correlationMap;

  public SqlToRelConverterOld(RelOptCluster cluster,
                           EntityResolver resolver,
                           SqlDialect dialect,
                           List<String> defaultQualifiers) {
    this.cluster = cluster;
    this.resolver = resolver;
    this.dialect = dialect;
    this.defaultQualifiers = defaultQualifiers;
    this.rexBuilder = cluster.getRexBuilder();
    this.typeFactory = cluster.getTypeFactory();
    this.cteMap = new HashMap<>();
    this.correlationMap = new HashMap<>();
  }

  public RelNode convert(SqlNode sqlNode) {
    // Reset correlation and CTE maps for each conversion
    correlationMap.clear();
    cteMap.clear();

    return convertSqlNodeRecursive(sqlNode, null);
  }

  private RelNode convertSqlNodeRecursive(SqlNode sqlNode, RelNode parentRel) {
    if (sqlNode instanceof SqlSelect) {
      return convertSqlSelect((SqlSelect) sqlNode, parentRel);
    }
    if (sqlNode instanceof SqlWith) {
      return convertSqlWith((SqlWith) sqlNode);
    }
    if (sqlNode instanceof SqlOrderBy) {
      return convertSqlOrderBy((SqlOrderBy) sqlNode, parentRel);
    }
    throw new UnsupportedOperationException("Unsupported SQL node type: " + sqlNode.getClass());
  }

  private RelNode convertSqlWith(SqlWith sqlWith) {
    // Process CTE definitions first
    for (SqlNode withItem : sqlWith.withList) {
      if (withItem instanceof SqlWithItem) {
        SqlWithItem cteItem = (SqlWithItem) withItem;
        String cteName = cteItem.name.getSimple();
        RelNode cteRel = convertSqlNodeRecursive(cteItem.query, null);
        cteMap.put(cteName, cteRel);
      }
    }

    // Convert main query
    return convertSqlNodeRecursive(sqlWith.body, null);
  }

  private RelNode convertSqlSelect(SqlSelect sqlSelect, RelNode parentRel) {
    // Handle DISTINCT
    boolean isDistinct = sqlSelect.isDistinct();

    // Handle FROM clause (table scan or join)
    RelNode fromRel = sqlSelect.getFrom() != null
        ? convertFromClause(sqlSelect.getFrom(), parentRel)
        : LogicalValues.createOneRow(cluster); // For queries without FROM

    // Set up correlation context for subqueries
//    setupCorrelationForSubqueries(sqlSelect, fromRel);

    // Handle WHERE clause (filter)
    RelNode filterRel = convertWhereClause(sqlSelect.getWhere(), fromRel);

    // Handle GROUP BY clause
    RelNode groupByRel = convertAggregation(sqlSelect, filterRel);//convertGroupByClause(sqlSelect.getGroup(), filterRel);

    // Handle HAVING clause
    RelNode havingRel = convertHavingClause(sqlSelect.getHaving(), groupByRel);

    // Handle SELECT clause (project)
    RelNode projectRel = convertSelectClause(sqlSelect.getSelectList(), havingRel);

    // Handle DISTINCT if present
    if (isDistinct) {
//      projectRel = convertDistinct(projectRel);
    }

    // Handle WINDOW clause
//    RelNode windowRel = convertWindowClause(sqlSelect.getWindowList(), projectRel);

    // Handle OFFSET and FETCH (LIMIT)
//    RelNode limitRel = convertOffsetFetch(sqlSelect.getOffset(), sqlSelect.getFetch(), windowRel);

//    return limitRel;
    return projectRel;
  }

  /**
   * Given a SqlSelect, identify if it's an aggregate call or not.
   */
  public boolean isAggregateCall(SqlSelect sqlSelect) {
    Objects.requireNonNull(sqlSelect, "sqlSelect cannot be null");

    // 1. Check for HAVING clause
    // HAVING clause is only allowed in aggregate queries.
    if (sqlSelect.getHaving() != null) {
      return true;
    }

    // 2. Check for GROUP BY clause
    // GROUP BY clause explicitly indicates aggregation.
    // getGroup() returns null if no GROUP BY. It returns a (possibly empty)
    // SqlNodeList if GROUP BY is present (e.g., GROUP BY () ).
    if (sqlSelect.getGroup() != null) {
      return true;
    }

    // 3. Check SELECT list for aggregate functions (excluding those within OVER)
    // If there's no GROUP BY or HAVING, aggregation occurs if an aggregate
    // function is used on the entire input (implicit global aggregation).
    if (sqlSelect.getSelectList() != null) {
      for (SqlNode selectItem : sqlSelect.getSelectList()) {
        // We need to check if the select item *contains* an aggregate function
        // that is NOT part of an OVER clause. SqlUtil.containsAgg is perfect for this.
        // It uses a visitor internally to traverse the expression tree.
        if (SqlUtil.containsAgg(selectItem)) {
          // Found a non-windowed aggregate function in the select list.
          return true;
        }
                 /*
                 // Alternative Manual Visitor approach (more complex, less recommended than SqlValidatorUtil.containsAggregate)
                 if (containsNonWindowedAggregate(selectItem)) {
                     return true;
                 }
                 */
      }
    }

    // If none of the above conditions are met, it's not an aggregate query.
    return false;
  }


//  private void setupCorrelationForSubqueries(SqlSelect sqlSelect, RelNode fromRel) {
//    // Set up correlation variables for subqueries in where, having, and select list
//    if (sqlSelect.getWhere() != null) {
//      extractCorrelationVariables(sqlSelect.getWhere(), fromRel);
//    }
//
//    if (sqlSelect.getHaving() != null) {
//      extractCorrelationVariables(sqlSelect.getHaving(), fromRel);
//    }
//
//    if (sqlSelect.getSelectList() != null) {
//      for (SqlNode node : sqlSelect.getSelectList().getList()) {
//        extractCorrelationVariables(node, fromRel);
//      }
//    }
//  }

//  private void extractCorrelationVariables(SqlNode node, RelNode fromRel) {
//    if (node instanceof SqlBasicCall) {
//      SqlBasicCall call = (SqlBasicCall) node;
//
//      for (SqlNode operand : call.getOperandList()) {
//        extractCorrelationVariables(operand, fromRel);
//      }
//
//      // Handle CORRELATE operator specially
//      if (call.getOperator().getKind() == SqlKind.CORRELATE) {
//        SqlNode left = call.operands[0];
//        SqlNode alias = call.operands[1];
//
//        if (alias instanceof SqlIdentifier) {
//          String correlName = ((SqlIdentifier) alias).getSimple();
//          RexCorrelVariable correlVar = rexBuilder.makeCorrel(
//              fromRel.getRowType(),
//              correlName
//          );
//          correlationMap.put(correlName, correlVar);
//        }
//      }
//    } else if (node instanceof SqlSelect) {
//      // Handle subqueries recursively
//      SqlSelect subquery = (SqlSelect) node;
//      if (subquery.getWhere() != null) {
//        extractCorrelationVariables(subquery.getWhere(), fromRel);
//      }
//    }
//  }

//  private RelNode convertDistinct(RelNode input) {
//    return LogicalAggregate.create(
//        input,
//        input.getRowType().getFieldList().stream()
//            .map(field -> rexBuilder.makeInputRef(input, field.getIndex()))
//            .collect(Collectors.toList()),
//        Collections.emptyList() // No aggregate functions
//    );
//  }

//  private RelNode convertWindowClause(SqlNodeList windowList, RelNode input) {
//    if (windowList == null || windowList.isEmpty()) {
//      return input;
//    }
//
//    // Convert window definitions to window specs
//    List<Window.Group> windowGroups = new ArrayList<>();
//
//    for (SqlNode windowNode : windowList.getList()) {
//      if (windowNode instanceof SqlWindow) {
//        SqlWindow window = (SqlWindow) windowNode;
//
//        // Create partition keys
//        List<RexNode> partitionKeys = new ArrayList<>();
//        if (window.getPartitionList() != null) {
//          for (SqlNode partitionItem : window.getPartitionList()) {
//            partitionKeys.add(convertSqlToRexNode(partitionItem, input));
//          }
//        }
//
//        // Create order keys
//        List<RelFieldCollation> orderKeys = new ArrayList<>();
//        if (window.getOrderList() != null) {
//          for (SqlNode orderItem : window.getOrderList()) {
//            SqlBasicCall call = (SqlBasicCall) orderItem;
//            RexNode sortExpr = convertSqlToRexNode(call.operands[0], input);
//            RelFieldCollation.Direction direction =
//                call.getOperator() == SqlStdOperatorTable.DESC
//                    ? RelFieldCollation.Direction.DESCENDING
//                    : RelFieldCollation.Direction.ASCENDING;
//
//            orderKeys.add(new RelFieldCollation(
//                input.getRowType().getFieldNames().indexOf(sortExpr.toString()),
//                direction
//            ));
//          }
//        }
//
//        // Create window spec
//        Window.Group windowGroup = new Window.Group(
//            partitionKeys,
//            orderKeys,
//            SqlWindow.createWindowBound(window.getLowerBound()),
//            SqlWindow.createWindowBound(window.getUpperBound()),
//            null // No RexWindowFunctions yet
//        );
//
//        windowGroups.add(windowGroup);
//      }
//    }
//
//    return LogicalWindow.create(
//        input,
//        windowGroups
//    );
//  }

//  private RelNode convertOffsetFetch(SqlNode offsetNode, SqlNode fetchNode, RelNode input) {
//    if (offsetNode == null && fetchNode == null) {
//      return input;
//    }
//
//    // Convert OFFSET
//    RexNode offset = offsetNode != null
//        ? convertSqlToRexNode(offsetNode, input)
//        : rexBuilder.makeExactLiteral(BigDecimal.ZERO);
//
//    // Convert FETCH/LIMIT
//    RexNode fetch = fetchNode != null
//        ? convertSqlToRexNode(fetchNode, input)
//        : rexBuilder.makeExactLiteral(new BigDecimal(-1)); // -1 means no limit
//
//    return LogicalSort.create(
//        input,
//        input.getTraitSet(),
//        Collections.emptyList(), // No ordering
//        offset,
//        fetch
//    );
//  }

  private RelNode convertSelectClause(SqlNodeList selectList, RelNode inputRel) {
    List<RexNode> projects = new ArrayList<>();
    List<String> fieldNames = new ArrayList<>();

    // Handle '*' case
    if (selectList.size() == 1 && selectList.get(0) instanceof SqlIdentifier
        && ((SqlIdentifier) selectList.get(0)).isStar()) {
      // For *, include all columns from input
      for (int i = 0; i < inputRel.getRowType().getFieldCount(); i++) {
        projects.add(rexBuilder.makeInputRef(inputRel, i));
        fieldNames.add(inputRel.getRowType().getFieldNames().get(i));
      }
    } else {
      // Process each item in SELECT list
      for (SqlNode selectItem : selectList.getList()) {
        if (selectItem instanceof SqlIdentifier && ((SqlIdentifier) selectItem).isStar()) {
          // Handle table qualified star: table.*
          String tableAlias = ((SqlIdentifier) selectItem).names.get(0);
          addColumnsForTableAlias(tableAlias, inputRel, projects, fieldNames);
        } else {
          // Regular column or expression
          RexNode rexProject = convertSqlToRexNode(selectItem, inputRel);
          projects.add(rexProject);

          // Handle column aliases in AS expressions
          String columnName = deriveColumnName(selectItem);
          fieldNames.add(columnName);

          // Handle aggregation functions
          if (isAggregateCall(selectItem)) {
            inputRel = addAggregateFunction(selectItem, inputRel, projects.size() - 1);
          }
        }
      }
    }

//    RelDataType rowType = typeFactory.createStructType(
//        projects.stream()
//            .map(RexNode::getType)
//            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll),
//        fieldNames
//    );

    return LogicalProject.create(
        inputRel,
        List.of(),
        projects,
        fieldNames,
//        rowType,
        Set.of()
    );
  }

//  private RelNode convertSelectClause(SqlNodeList selectList, RelNode inputRel) {
//    List<RexNode> projects = new ArrayList<>();
//    List<String> fieldNames = new ArrayList<>();
//
//    for (SqlNode selectItem : selectList.getList()) {
//      RexNode rexProject = convertSqlToRexNode(selectItem, inputRel);
//      projects.add(rexProject);
//
//      // Derive column names
//      String columnName = deriveColumnName(selectItem);
//      fieldNames.add(columnName);
//    }
//
//    RelDataType rowType = typeFactory.createStructType(
//        projects.stream()
//            .map(RexNode::getType)
//            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll),
//        fieldNames
//    );
//
//    return LogicalProject.create(
//        inputRel,
//        projects,
//        rowType
//    );
//  }

  private void addColumnsForTableAlias(String tableAlias, RelNode inputRel,
                                       List<RexNode> projects, List<String> fieldNames) {
    // Find all columns from the specified table
    RelDataType rowType = inputRel.getRowType();
    for (int i = 0; i < rowType.getFieldCount(); i++) {
      String fullName = rowType.getFieldNames().get(i);
      if (fullName.startsWith(tableAlias + ".") || tableAlias.equals("*")) {
        projects.add(rexBuilder.makeInputRef(inputRel, i));
        fieldNames.add(rowType.getFieldNames().get(i));
      }
    }
  }

  private boolean isAggregateCall(SqlNode node) {
    if (node instanceof SqlBasicCall) {
      SqlBasicCall call = (SqlBasicCall) node;
      SqlOperator operator = call.getOperator();
      return operator.getKind() == SqlKind.SUM ||
          operator.getKind() == SqlKind.COUNT ||
          operator.getKind() == SqlKind.MIN ||
          operator.getKind() == SqlKind.MAX ||
          operator.getKind() == SqlKind.AVG;
    }
    return false;
  }

  private RelNode addAggregateFunction(SqlNode aggNode, RelNode input, int projIndex) {
    if (aggNode instanceof SqlBasicCall) {
      SqlBasicCall call = (SqlBasicCall) aggNode;

      // Create AggregateCall
      SqlAggFunction aggFunction = (SqlAggFunction) call.getOperator();
      List<Integer> argList = new ArrayList<>();

      // For COUNT(*), use empty arg list
      if (aggFunction.getKind() == SqlKind.COUNT &&
          call.getOperandList().size() == 1 &&
          call.getOperandList().get(0) instanceof SqlIdentifier &&
          ((SqlIdentifier) call.getOperandList().get(0)).isStar()) {
        // COUNT(*) - empty arg list
      } else {
        // Other agg functions - add arguments
        for (SqlNode operand : call.getOperandList()) {
          if (operand instanceof SqlIdentifier) {
            argList.add(findColumnIndex(input, ((SqlIdentifier) operand).getSimple()));
          }
        }
      }

      AggregateCall aggregateCall = AggregateCall.create(
          aggFunction,
          false, // not distinct
          false, // not approximate
          argList,
          -1, // no filter
          null, // no distinctKeys
          call.getOperator().getName()
      );

      // Create an Aggregate node
//      return LogicalAggregate.create(
//          input,
//          Collections.emptyList(), // No group keys for simple agg
//          Collections.singletonList(aggregateCall)
//      );
      throw new UnsupportedOperationException("Aggregate functions are not supported yet");
    }

    return input;
  }

  private RelNode convertFromClause(SqlNode fromNode, RelNode parentRel) {
    if (fromNode.getKind() == SqlKind.IDENTIFIER) {
      // Simple table reference
      List<String> tableNames = ((SqlIdentifier) fromNode).names;
      return convertTableScan(tableNames);
    }
    if (fromNode instanceof SqlBasicCall) {
      SqlBasicCall fromCall = (SqlBasicCall) fromNode;

      // Handle JOIN
      if (fromCall.getKind() == SqlKind.JOIN) {
        return convertJoinClause(fromCall, parentRel);
      }

      // Handle subquery
      if (fromCall.getOperator() == SqlStdOperatorTable.AS) {
        throw new UnsupportedOperationException("Subquery aliasing is not supported yet");
//        return convertSubqueryAlias(fromCall, parentRel);
      }
    }
    if (fromNode instanceof SqlSelect) {
      // Subquery in FROM clause
      return convertSqlNodeRecursive(fromNode, parentRel);
    }
    throw new UnsupportedOperationException("Unsupported FROM clause type: " + fromNode.getClass());
  }

  private RelNode convertTableScan(List<String> tableQualifiers) {
    String tableName = tableQualifiers.get(tableQualifiers.size() - 1);
    // Check if it's a CTE
    if (cteMap.containsKey(tableName)) {
      return cteMap.get(tableName);
    }

    EntityQualifier qualifier = new EntityQualifier(tableQualifiers, defaultQualifiers, dialect);
    // Regular table scan
    DatabaseEntity entity = resolver.resolve(qualifier);
    if (!(entity instanceof RelationalEntity)) {
        throw new IllegalArgumentException("Expected a RelationalEntity but got " + entity.getClass().getSimpleName());
    }
    RelationalEntity relationalEntity = (RelationalEntity) entity;
    RelOptTable relOptTable = createRelOptTable(relationalEntity);
    return LogicalTableScan.create(
        cluster,
        relOptTable,
        List.of()
    );
  }

  private RelOptTable createRelOptTable(RelationalEntity relationalEntity) {
    ImmutableList<String> qualifiedName = getQualifiedName(relationalEntity);
    ScannableTable scannableTable = ScannableTable.create(relationalEntity);
    RelDataType rowType = scannableTable.getRowType(cluster.getTypeFactory());
    return RelOptTableImpl.create(null, rowType, scannableTable, qualifiedName);
  }

  private static ImmutableList<String> getQualifiedName(RelationalEntity relationalEntity) {
    DatabaseEntity dbEntity = (DatabaseEntity) relationalEntity;
    List<String> namespace = dbEntity.getNamespace().stream().map(Identifier::getNormalizedName).toList();
    return ImmutableList.<String>builder()
        .addAll(namespace)
        .add(dbEntity.getName().getNormalizedName())
        .build();
  }

  private RelNode convertJoinClause(SqlBasicCall joinCall, RelNode parentRel) {
    SqlNode left = joinCall.getOperandList().get(0);
    SqlNode right = joinCall.getOperandList().get(1);
    SqlNode condition = joinCall.getOperandList().get(2);

    RelNode leftRel = convertFromClause(left, parentRel);
    RelNode rightRel = convertFromClause(right, parentRel);

    // Convert join condition to RexNode
    // RexNode joinCondition = convertSqlToRexNode(condition, leftRel, rightRel);

    // Determine join type
//    JoinRelType joinType = determineJoinType(joinCall.getOperator());

//    return LogicalJoin.create(
//        leftRel,
//        rightRel,
//        joinCondition,
//        joinType
//    );
    throw new UnsupportedOperationException("Join operations are not supported yet");
  }

//  private RelNode convertSubqueryAlias(SqlBasicCall aliasCall, RelNode parentRel) {
//    SqlNode subquery = aliasCall.operands[0];
//    SqlIdentifier alias = (SqlIdentifier) aliasCall.operands[1];
//
//    RelNode subqueryRel = convertSqlNodeRecursive(subquery, parentRel);
//
//    // Add correlation for subquery alias
//    RexCorrelVariable correlVar = rexBuilder.makeCorrel(
//        subqueryRel.getRowType(),
//        alias.getSimple()
//    );
//    correlationMap.put(alias.getSimple(), correlVar);
//
//    return subqueryRel;
//  }

  private RelNode convertWhereClause(SqlNode whereNode, RelNode inputRel) {
    if (whereNode == null) {
      return inputRel;
    }

    RexNode condition = convertSqlToRexNode(whereNode, inputRel);
    return LogicalFilter.create(inputRel, condition);
  }

  private RelNode convertAggregation(SqlSelect sqlSelect, RelNode inputRel) {
    SqlNodeList groupList = sqlSelect.getGroup();
    SqlNode havingNode = sqlSelect.getHaving();

    // If there's no GROUP BY or aggregate functions in the SELECT list or HAVING clause,
    // then we don't need an aggregation node
    if ((groupList == null || groupList.isEmpty()) &&
        !containsAggregates(sqlSelect.getSelectList()) &&
        (havingNode == null || !containsAggregates(havingNode))) {
      return inputRel;
    }

    // Build the GROUP BY bit set
    ImmutableBitSet.Builder groupSetBuilder = ImmutableBitSet.builder();

    // Process GROUP BY expressions
    if (groupList != null && !groupList.isEmpty()) {
      for (SqlNode groupItem : groupList.getList()) {
        RexNode rex = convertSqlToRexNode(groupItem, inputRel);
        if (rex instanceof RexInputRef) {
          groupSetBuilder.set(((RexInputRef) rex).getIndex());
        } else {
          // For expressions in GROUP BY, we need to add them to project list first
          // and then reference the projected expression
          LogicalProject projectWithGroupExpr = LogicalProject.create(
              inputRel,
              List.of(),
              List.of(rex),
              List.of("group_expr_" + inputRel.getRowType().getFieldCount()),
              Set.of()
          );
          inputRel = projectWithGroupExpr;
          groupSetBuilder.set(inputRel.getRowType().getFieldCount() - 1);
        }
      }
    }

    // Build the aggregate calls list
    List<AggregateCall> aggCalls = new ArrayList<>();

    // Process aggregate functions in SELECT list
    if (sqlSelect.getSelectList() != null) {
      processAggregatesInNodeList(sqlSelect.getSelectList(), inputRel, aggCalls);
    }

    // Process aggregate functions in HAVING clause
    if (havingNode != null) {
      processAggregatesInNode(havingNode, inputRel, aggCalls);
    }

    // Create the Aggregate node
    ImmutableBitSet groupSet = groupSetBuilder.build();
    return LogicalAggregate.create(
        inputRel,
        ImmutableList.of(), // No hints
        groupSet,
        null, // No additional grouping sets
        aggCalls
    );
  }

  private boolean containsAggregates(SqlNode node) {
    if (node == null) return false;

    final AtomicBoolean hasAggregates = new AtomicBoolean(false);

    node.accept(new SqlBasicVisitor<Void>() {
      @Override
      public Void visit(SqlCall call) {
        if (call.getOperator() instanceof SqlAggFunction) {
          hasAggregates.set(true);
          return null;
        }
        return super.visit(call);
      }
    });

    return hasAggregates.get();
  }

  private void processAggregatesInNodeList(SqlNodeList nodeList, RelNode inputRel, List<AggregateCall> aggCalls) {
    for (SqlNode node : nodeList.getList()) {
      processAggregatesInNode(node, inputRel, aggCalls);
    }
  }

  private void processAggregatesInNode(SqlNode node, RelNode inputRel, List<AggregateCall> aggCalls) {
    if (node instanceof SqlBasicCall) {
      SqlBasicCall call = (SqlBasicCall) node;

      if (call.getOperator() instanceof SqlAggFunction) {
        // Process aggregate function
        SqlAggFunction aggFunction = (SqlAggFunction) call.getOperator();
        List<Integer> argList = new ArrayList<>();
        boolean isDistinct = call.getFunctionQuantifier() != null &&
                call.getFunctionQuantifier().toString().equalsIgnoreCase("DISTINCT");

        // For COUNT(*), use empty arg list
        if (aggFunction.getKind() == SqlKind.COUNT &&
                call.getOperandList().size() == 1 &&
                call.getOperandList().get(0) instanceof SqlIdentifier &&
                ((SqlIdentifier) call.getOperandList().get(0)).isStar()) {
          // COUNT(*) - empty arg list remains empty
        } else {
          // Other agg functions - add arguments
          for (SqlNode operand : call.getOperandList()) {
            RexNode rexNode = convertSqlToRexNode(operand, inputRel);
            if (rexNode instanceof RexInputRef) {
              argList.add(((RexInputRef) rexNode).getIndex());
            } else {
              // For complex expressions, we need to project them first
              LogicalProject projectWithAggExpr = LogicalProject.create(
                      inputRel,
                      ImmutableList.of(),
                      ImmutableList.of(rexNode),
                      ImmutableList.of("agg_expr_" + inputRel.getRowType().getFieldCount()),
                      java.util.Set.of()
              );
              inputRel = projectWithAggExpr;
              argList.add(inputRel.getRowType().getFieldCount() - 1);
            }
          }
        }

        AggregateCall aggregateCall = AggregateCall.create(
                aggFunction,
                isDistinct,
                false, // not approximate
                aggFunction.allowsNullTreatment() && aggFunction.getKind() != SqlKind.COUNT, // ignoreNulls - typically true except for count
                ImmutableList.of(), // empty rexList
                argList,
                -1, // no filter
                null, // no distinctKeys
                RelCollations.EMPTY, // no collation
                typeFactory.createSqlType(SqlTypeName.ANY), // type will be inferred
                deriveAggCallName(call, aggCalls.size())
        );

        aggCalls.add(aggregateCall);
      } else {
        // Process operands recursively
        for (SqlNode operand : call.getOperandList()) {
          processAggregatesInNode(operand, inputRel, aggCalls);
        }
      }
    } else if (node instanceof SqlSelect) {
      // Check for aggregates in subquery
      SqlSelect subSelect = (SqlSelect) node;
      if (subSelect.getSelectList() != null) {
        for (SqlNode item : subSelect.getSelectList().getList()) {
          processAggregatesInNode(item, inputRel, aggCalls);
        }
      }
      if (subSelect.getHaving() != null) {
        processAggregatesInNode(subSelect.getHaving(), inputRel, aggCalls);
      }
    } else if (node instanceof SqlNodeList) {
      // Process each node in the list
      SqlNodeList nodeList = (SqlNodeList) node;
      for (SqlNode item : nodeList.getList()) {
        processAggregatesInNode(item, inputRel, aggCalls);
      }
    }
    // Other types of SqlNodes don't contain aggregates
  }

//  private void processAggregatesInNode(SqlNode node, RelNode inputRel, List<AggregateCall> aggCalls) {
//    if (node instanceof SqlBasicCall) {
//      SqlBasicCall call = (SqlBasicCall) node;
//
//      if (call.getOperator() instanceof SqlAggFunction) {
//        // Process aggregate function
//        SqlAggFunction aggFunction = (SqlAggFunction) call.getOperator();
//        List<Integer> argList = new ArrayList<>();
//        boolean isDistinct = call.getFunctionQuantifier() != null &&
//                call.getFunctionQuantifier().toString().equalsIgnoreCase("DISTINCT");
//
//        // For COUNT(*), use empty arg list
//        if (aggFunction.getKind() == SqlKind.COUNT &&
//            call.getOperandList().size() == 1 &&
//            call.getOperandList().get(0) instanceof SqlIdentifier &&
//            ((SqlIdentifier) call.getOperandList().get(0)).isStar()) {
//          // COUNT(*) - empty arg list
//        } else {
//          // Other agg functions - add arguments
//          for (SqlNode operand : call.getOperandList()) {
//            RexNode rexNode = convertSqlToRexNode(operand, inputRel);
//            if (rexNode instanceof RexInputRef) {
//              argList.add(((RexInputRef) rexNode).getIndex());
//            } else {
//              // For complex expressions, we need to project them first
//              LogicalProject projectWithAggExpr = LogicalProject.create(
//                  inputRel,
//                  List.of(),
//                  List.of(rexNode),
//                  List.of("agg_expr_" + inputRel.getRowType().getFieldCount()),
//                  Set.of()
//              );
//              inputRel = projectWithAggExpr;
//              argList.add(inputRel.getRowType().getFieldCount() - 1);
//            }
//          }
//        }
//
////        SqlAggFunction aggFunction,
////        boolean distinct, boolean approximate, boolean ignoreNulls,
////        List<RexNode> rexList, List<Integer> argList, int filterArg,
////        @Nullable ImmutableBitSet distinctKeys, RelCollation collation,
////        int groupCount,
////        RelNode input, @Nullable RelDataType type, @Nullable String name
//        AggregateCall aggregateCall = AggregateCall.create(
//            aggFunction,
//            isDistinct,
//            false, // not approximate
//                true, // ignore nulls
//            argList,
//            -1, // no filter
//            null, // no distinctKeys
//            null, // no sort
//            false, // ignore nulls
//            List.of(), // no collations
//            deriveAggCallName(call, aggCalls.size())
//        );
//
//        aggCalls.add(aggregateCall);
//      } else {
//        // Process operands recursively
//        for (SqlNode operand : call.getOperandList()) {
//          processAggregatesInNode(operand, inputRel, aggCalls);
//        }
//      }
//    }
//  }

  private String deriveAggCallName(SqlBasicCall aggCall, int index) {
    if (aggCall.getOperator() instanceof SqlAggFunction) {
      return aggCall.getOperator().getName().toLowerCase() + "$" + index;
    }
    return "agg$" + index;
  }

  private RelNode convertGroupByClause(SqlNodeList groupList, RelNode inputRel) {
    if (groupList == null || groupList.size() == 0) {
      return inputRel;
    }

//    List<RexNode> groupExprs = new ArrayList<>();
    ImmutableBitSet.Builder groupSetBuilder = ImmutableBitSet.builder();
    for (SqlNode groupItem : groupList.getList()) {
      RexNode rex = convertSqlToRexNode(groupItem, inputRel);
      groupSetBuilder.set(((RexInputRef) rex).getIndex());
    }

//    for (int i = 0; i < groupExprs.size(); i++) {
//      groupSetBuilder.set(i);
//    }
    ImmutableBitSet groupSet = groupSetBuilder.build();

    return LogicalAggregate.create(
        inputRel,
        ImmutableList.of(), // No hints
        groupSet,
        null, // No additional grouping sets
        Collections.emptyList() // No aggregation functions for now
    );
  }

  private RelNode convertHavingClause(SqlNode havingNode, RelNode inputRel) {
    if (havingNode == null) {
      return inputRel;
    }

    RexNode condition = convertSqlToRexNode(havingNode, inputRel);
    return LogicalFilter.create(inputRel, condition);
  }

  private RelNode convertSqlOrderBy(SqlOrderBy orderByNode, RelNode parentRel) {
    RelNode inputRel = convertSqlNodeRecursive(orderByNode.query, parentRel);

    List<RexNode> sortExprs = new ArrayList<>();
    List<RelFieldCollation> collations = new ArrayList<>();

    for (SqlNode orderItem : orderByNode.orderList.getList()) {
      if (orderItem instanceof SqlBasicCall) {
        SqlBasicCall call = (SqlBasicCall) orderItem;
        RexNode sortExpr = convertSqlToRexNode(call.getOperandList().get(0), inputRel);
        sortExprs.add(sortExpr);

        // Determine sort direction
        RelFieldCollation.Direction direction =
            call.getOperator() == SqlStdOperatorTable.DESC
                ? RelFieldCollation.Direction.DESCENDING
                : RelFieldCollation.Direction.ASCENDING;

        collations.add(
            new RelFieldCollation(
                sortExprs.size() - 1,
                direction
            )
        );
      }
    }

//    return LogicalSort.create(
//        inputRel,
//        collations
//    );
    throw new UnsupportedOperationException("ORDER BY is not supported yet");
  }

  private RexNode convertSqlToRexNode(SqlNode sqlNode, RelNode... inputRels) {
    // Merge multiple input rels if provided
    RelNode inputRel = inputRels.length > 0 ? mergeRelNodes(inputRels) : null;

    if (sqlNode.getKind() == SqlKind.IDENTIFIER) {
      return convertSqlIdentifier((SqlIdentifier) sqlNode, inputRel);
    }

    if (sqlNode instanceof SqlBasicCall) {
      return convertSqlBasicCall((SqlBasicCall) sqlNode, inputRel);
    }

    if (sqlNode.getKind() == SqlKind.LITERAL) {
      return convertSqlLiteral((SqlLiteral) sqlNode);
    }

    if (sqlNode.getKind() == SqlKind.SELECT) {
      return convertSubqueryToRexNode((SqlSelect) sqlNode, inputRel);
    }

    throw new UnsupportedOperationException("Unsupported SQL node type for Rex conversion: " + sqlNode.getClass());
  }

  private RexNode convertSqlIdentifier(SqlIdentifier identifier, RelNode inputRel) {
    // Check for correlation variable
    if (correlationMap.containsKey(identifier.names.get(0))) {
      return correlationMap.get(identifier.names.get(0));
    }

    // Find column index in input rel
    String columnName = identifier.names.get(0);
    int columnIndex = findColumnIndex(inputRel, columnName);

    return rexBuilder.makeInputRef(inputRel, columnIndex);
  }

  private RexNode convertSqlBasicCall(SqlBasicCall call, RelNode inputRel) {
    List<RexNode> operands = new ArrayList<>();

    for (SqlNode operand : call.getOperandList()) {
      operands.add(convertSqlToRexNode(operand, inputRel));
    }

    SqlTypeName returnType = SqlTypeName.ANY;//call.getOperator().getReturnType();
    return rexBuilder.makeCall(
        typeFactory.createSqlType(returnType),
        call.getOperator(),
        operands
    );
  }

  private RexNode convertSqlLiteral(SqlLiteral literal) {
    // Convert SQL literal to RexLiteral
    return rexBuilder.makeLiteral(
        literal.getValue(),
        typeFactory.createSqlType(literal.getTypeName()),
        false
    );
  }

  private RexNode convertSubqueryToRexNode(SqlSelect subquery, RelNode correlatedRel) {
    // Handle correlated subquery
    RelNode subqueryRel = convertSqlNodeRecursive(subquery, correlatedRel);

    // Convert subquery to a scalar subquery
    return RexSubQuery.scalar(
//        subqueryRel.getRowType(),
        subqueryRel
    );
  }

  private RelNode mergeRelNodes(RelNode... inputRels) {
    if (inputRels.length == 1) {
      return inputRels[0];
    }

    // Create a logical union of input rels
    return LogicalUnion.create(
        Arrays.asList(inputRels),
        true // all inputs must have same row type
    );
  }

//  private JoinRelType determineJoinType(SqlOperator operator) {
//    if (operator == SqlStdOperatorTable.INNER_JOIN) return JoinRelType.INNER;
//    if (operator == SqlStdOperatorTable.LEFT_JOIN) return JoinRelType.LEFT;
//    if (operator == SqlStdOperatorTable.RIGHT_JOIN) return JoinRelType.RIGHT;
//    if (operator == SqlStdOperatorTable.FULL_JOIN) return JoinRelType.FULL;
//    throw new UnsupportedOperationException("Unsupported join type: " + operator);
//  }

  private String deriveColumnName(SqlNode selectItem) {
    if (selectItem instanceof SqlIdentifier) {
      return ((SqlIdentifier) selectItem).names.get(0);
    }
    if (selectItem instanceof SqlBasicCall) {
      return selectItem.toString();
    }
    return "expr";
  }

  private int findColumnIndex(RelNode inputRel, String columnName) {
    RelDataType rowType = inputRel.getRowType();
    for (int i = 0; i < rowType.getFieldCount(); i++) {
      if (rowType.getFieldNames().get(i).equalsIgnoreCase(columnName)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Column not found: " + columnName + ", rowType:" + rowType);
  }
}