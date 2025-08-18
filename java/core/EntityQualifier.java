package com.calcite_new.core.model;

import com.calcite_new.core.dialect.Dialect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Class representing the qualifiers for an entity. Given default qualification and actual qualification,
 * it creates a list of identifiers that represent the full qualification of the entity.
 * <p>
 * For example, if the default qualification is <code>[database, schema]</code> and the actual qualification is [table],
 * the full qualification will be <code>[database, schema, table]</code>.
 *
 * <p>
 * OR if the default qualification is <code>[database, schema]</code> and the actual qualification is <code>[schema2, table]</code>,
 * the full qualification will be <code>[database, schema2, table]</code>.
 *
 * <p>
 * OR if the default qualification is <code>[database, schema]</code> and the actual qualification is <code>[db, sch, tbl]</code>,
 * the full qualification will be <code>[db, sch, tbl]</code>.
 */
public class EntityQualifier {
  private final List<String> qualifiers;
  private final List<String> defaultQualifiers;
  private final Dialect dialect;
  private final List<Identifier> fullQualifiers;

  public EntityQualifier(List<String> qualifiers, List<String> defaultQualifiers, Dialect dialect) {
    this.qualifiers = qualifiers;
    this.defaultQualifiers = defaultQualifiers;
    this.dialect = dialect;
    this.fullQualifiers = initQualifiers();
    assert qualifiers.size() + defaultQualifiers.size() >= dialect.supportedQualificationLevels();
  }

  private List<Identifier> initQualifiers() {
    int supportedQualificationLevels = dialect.supportedQualificationLevels();
    Deque<Identifier> actualQualifiers = new ArrayDeque<>();
    for (int i = 0; i < supportedQualificationLevels; i++) {
      if (qualifiers.size() > i) {
        actualQualifiers.push(createId(qualifiers.get(qualifiers.size() - i - 1)));
        continue;
      }
      int idx = supportedQualificationLevels - i - 1;
      actualQualifiers.push(createId(defaultQualifiers.get(idx)));
    }
//    actualQualifiers.push(createId(dialect.product.name()));
    return actualQualifiers.stream().toList();
  }


  public List<Identifier> getQualifiers() {
    return fullQualifiers;
  }

  private Identifier createId(String name) {
    return Identifier.of(name, dialect);
  }

  @Override
  public String toString() {
    return "EntityQualifier" + fullQualifiers;
  }
}
