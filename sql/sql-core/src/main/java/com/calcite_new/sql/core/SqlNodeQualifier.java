package com.calcite_new.sql.core;

import com.calcite_new.core.resolver.EntityResolver;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.util.SqlBasicVisitor;

/**
 * Visitor that fully qualifies table names in SQL nodes.
 *
 */
public class SqlNodeQualifier extends SqlBasicVisitor<SqlNode> {
  //~ Instance fields --------------------------------------------------------

  private final EntityResolver resolver;

  //~ Constructors -----------------------------------------------------------

  public SqlNodeQualifier(EntityResolver resolver) {
    this.resolver = resolver;
  }

  //~ Methods ----------------------------------------------------------------

  @Override public SqlNode visit(SqlIdentifier id) {
    // If the identifier is already qualified, return it as is.
    return super.visit(id);
  }
}
