package com.calcite_new.sql.core.converter;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.*;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlWindow;
import org.apache.calcite.util.ControlFlowException;
import org.apache.calcite.util.Util;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

public class CustomRexOver extends RexCall {
  private static final Finder FINDER = new Finder();

  //~ Instance fields --------------------------------------------------------

  private final RexWindow window;
  private final boolean distinct;
  private final boolean ignoreNulls;

  //~ Constructors -----------------------------------------------------------

  /**
   * Creates a RexOver.
   *
   * <p>For example, "SUM(DISTINCT x) OVER (ROWS 3 PRECEDING)" is represented
   * as:
   *
   * <ul>
   * <li>type = Integer,
   * <li>op = {@link org.apache.calcite.sql.fun.SqlStdOperatorTable#SUM},
   * <li>operands = { {@link RexFieldAccess}("x") }
   * <li>window = {@link SqlWindow}(ROWS 3 PRECEDING)
   * </ul>
   *
   * @param type     Result type
   * @param op       Aggregate operator
   * @param operands Operands list
   * @param window   Window specification
   * @param distinct Aggregate operator is applied on distinct elements
   */
  public CustomRexOver(
      RelDataType type,
      SqlAggFunction op,
      List<RexNode> operands,
      RexWindow window,
      boolean distinct,
      boolean ignoreNulls) {
    super(type, op, operands);
    checkArgument(op.isAggregator());
    this.window = requireNonNull(window, "window");
    this.distinct = distinct;
    this.ignoreNulls = ignoreNulls;
  }

  //~ Methods ----------------------------------------------------------------

  /**
   * Returns the aggregate operator for this expression.
   */
  public SqlAggFunction getAggOperator() {
    return (SqlAggFunction) getOperator();
  }

  public RexWindow getWindow() {
    return window;
  }

  public boolean isDistinct() {
    return distinct;
  }

  public boolean ignoreNulls() {
    return ignoreNulls;
  }

  @Override protected String computeDigest(boolean withType) {
    final StringBuilder sb = new StringBuilder(op.getName());
    sb.append("(");
    if (distinct) {
      sb.append("DISTINCT ");
    }
    appendOperands(sb);
    sb.append(")");
    if (ignoreNulls) {
      sb.append(" IGNORE NULLS");
    }
    if (withType) {
      sb.append(":");
      sb.append(type.getFullTypeString());
    }
    sb.append(" OVER (")
        .append(window.toString())
        .append(")");
    return sb.toString();
  }

  @Override public int nodeCount() {
    return super.nodeCount() + window.nodeCount;
  }

  /**
   * Returns whether an expression contains an OVER clause.
   */
  public static boolean containsOver(RexNode expr) {
    try {
      expr.accept(FINDER);
      return false;
    } catch (OverFound e) {
      Util.swallow(e, null);
      return true;
    }
  }

  /**
   * Returns whether a program contains an OVER clause.
   */
  public static boolean containsOver(RexProgram program) {
    try {
      RexUtil.apply(FINDER, program.getExprList(), null);
      return false;
    } catch (OverFound e) {
      Util.swallow(e, null);
      return true;
    }
  }

  /**
   * Returns whether an expression list contains an OVER clause.
   */
  public static boolean containsOver(List<? extends RexNode> exprs,
                                     @Nullable RexNode condition) {
    try {
      RexUtil.apply(FINDER, exprs, condition);
      return false;
    } catch (OverFound e) {
      Util.swallow(e, null);
      return true;
    }
  }

  @Override public RexCall clone(RelDataType type, List<RexNode> operands) {
    throw new UnsupportedOperationException();
  }

  //~ Inner Classes ----------------------------------------------------------

  /** Exception thrown when an OVER is found. */
  private static class OverFound extends ControlFlowException {
    public static final OverFound INSTANCE = new OverFound();
  }

  /**
   * Visitor which detects a {@link org.apache.calcite.rex.RexOver} inside a {@link RexNode}
   * expression.
   *
   * <p>It is re-entrant (two threads can use an instance at the same time)
   * and it can be re-used for multiple visits.
   */
  private static class Finder extends RexVisitorImpl<Void> {
    Finder() {
      super(true);
    }

    @Override
    public Void visitCall(RexCall call) {
      if (call instanceof CustomRexOver) {
        throw OverFound.INSTANCE;
      }
      return super.visitCall(call);
    }
  }

  @Override public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    CustomRexOver rexOver = (CustomRexOver) o;
    return distinct == rexOver.distinct
        && ignoreNulls == rexOver.ignoreNulls
        && window.equals(rexOver.window)
        && op.allowsFraming() == rexOver.op.allowsFraming();
  }

  @Override public int hashCode() {
    if (hash == 0) {
      hash =
          Objects.hash(super.hashCode(), window, distinct, ignoreNulls,
              op.allowsFraming());
    }
    return hash;
  }
}
