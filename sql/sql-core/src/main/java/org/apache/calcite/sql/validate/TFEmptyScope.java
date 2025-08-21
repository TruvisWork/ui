package org.apache.calcite.sql.validate;

import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import org.apache.calcite.jdbc.CalciteSchema;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.prepare.RelOptTableImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.StructKind;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.Wrapper;
import org.apache.calcite.util.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * Deviant implementation of {@link SqlValidatorScope} for the top of the scope
 */
public class TFEmptyScope extends EmptyScope {
  private final SqlDialect dialect;

  public TFEmptyScope(SqlDialect dialect, SqlValidatorImpl validator) {
    super(validator);
    this.dialect = dialect;
  }


  @Override public void resolveTable(List<String> names, SqlNameMatcher nameMatcher,
                                     Path path, Resolved resolved) {
    final List<Resolve> imperfectResolves = new ArrayList<>();
    final List<Resolve> resolves = ((ResolvedImpl) resolved).resolves;

    // Look in the default schema, then default catalog, then root schema.
    for (List<String> schemaPath : validator.catalogReader.getSchemaPaths()) {
      resolve_(validator.catalogReader.getRootSchema(), names, schemaPath,
          nameMatcher, path, resolved);
      for (Resolve resolve : resolves) {
        if (resolve.remainingNames.isEmpty()) {
          // There is a full match. Return it as the only match.
          ((ResolvedImpl) resolved).clear();
          resolves.add(resolve);
          return;
        }
      }
      imperfectResolves.addAll(resolves);
    }
    // If there were no matches in the last round, restore those found in
    // previous rounds
    if (resolves.isEmpty()) {
      resolves.addAll(imperfectResolves);
    }
  }

  private void resolve_(final CalciteSchema rootSchema, List<String> names,
                        List<String> schemaNames, SqlNameMatcher nameMatcher, Path path,
                        Resolved resolved) {
    EntityQualifier qualifier = new EntityQualifier(names, schemaNames, dialect);
    final List<String> concat = qualifier.getQualifiers().stream().map(Identifier::getNormalizedName).toList();
    CalciteSchema schema = rootSchema;
    SqlValidatorNamespace namespace = null;
    List<String> remainingNames = concat;
    for (String schemaName : concat) {
      if (schema == rootSchema
          && nameMatcher.matches(schemaName, schema.name)) {
        remainingNames = Util.skip(remainingNames);
        continue;
      }
      final CalciteSchema subSchema =
          schema.getSubSchema(schemaName, nameMatcher.isCaseSensitive());
      if (subSchema != null) {
        path = path.plus(null, -1, subSchema.name, StructKind.NONE);
        remainingNames = Util.skip(remainingNames);
        schema = subSchema;
        namespace = new SchemaNamespace(validator, path.stepNames());
        continue;
      }
      CalciteSchema.TableEntry entry =
          schema.getTable(schemaName, nameMatcher.isCaseSensitive());
      if (entry == null) {
        entry =
            schema.getTableBasedOnNullaryFunction(schemaName,
                nameMatcher.isCaseSensitive());
      }
      if (entry != null) {
        path = path.plus(null, -1, entry.name, StructKind.NONE);
        remainingNames = Util.skip(remainingNames);
        final Table table = entry.getTable();
        SqlValidatorTable table2 = null;
        if (table instanceof Wrapper) {
          table2 = ((Wrapper) table).unwrap(Prepare.PreparingTable.class);
        }
        if (table2 == null) {
          final RelOptSchema relOptSchema =
              validator.catalogReader.unwrap(RelOptSchema.class);
          final RelDataType rowType = table.getRowType(validator.typeFactory);
          table2 = RelOptTableImpl.create(relOptSchema, rowType, entry, null);
        }
        namespace = new TableNamespace(validator, table2);
        resolved.found(namespace, false, this, path, remainingNames);
        return;
      }
      // neither sub-schema nor table
      if (namespace != null
          && !remainingNames.equals(names)) {
        resolved.found(namespace, false, this, path, remainingNames);
      }
      return;
    }
  }

}
