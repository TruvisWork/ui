package com.calcite_new.core.model;

import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * EntityCatalog is a schema that contains a collection of namespaces and tables.
 */
public class EntityCatalog extends AbstractSchema {
  private Map<Identifier, Namespace> products = new ConcurrentHashMap<>();

  public DatabaseEntity getDatabaseEntity(EntityQualifier qualifier) {
    List<Identifier> qualifiedName = qualifier.getQualifiers();
    if (qualifiedName.size() < 2) {
      throw new IllegalArgumentException("Qualified name must have at least two parts");
    }
    Namespace namespace = getNamespace(qualifiedName.subList(0, qualifiedName.size() - 1));
    if (namespace == null) {
      return null;
    }
    Identifier entityId = qualifiedName.get(qualifiedName.size() - 1);
    DatabaseEntity table = namespace.getTable(entityId);
    if (table != null) return table;
    DatabaseEntity extTable = namespace.getExternalTable(entityId);
    if (extTable != null) return extTable;
    DatabaseEntity view = namespace.getView(entityId);
    if (view != null) return view;
    return null;
  }

  public void addEntity(DatabaseEntity entity) {
    List<Identifier> namespace = entity.getNamespace();
    Namespace ns = getOrAddNamespace(namespace);
    if (entity.getKind() == EntityKind.TABLE) {
      ns.addTable((Table) entity);
    } else if (entity.getKind() == EntityKind.EXTERNAL_TABLE) {
      ns.addExternalTable((com.calcite_new.core.model.entity.ExternalTable) entity);
    } else if (entity.getKind() == EntityKind.VIEW) {
      ns.addView((View) entity);
    } else {
      throw new IllegalArgumentException("Unsupported entity kind: " + entity.getKind());
    }
  }

  private Namespace getOrAddNamespace(List<Identifier> namespace) {
    Namespace ns = products.computeIfAbsent(namespace.get(0), Namespace::new);
    for (Identifier nsName : namespace.subList(1, namespace.size())) {
      ns = ns.getOrAddNamespace(nsName);
    }
    return ns;
  }

  private Namespace getNamespace(List<Identifier> identifiers) {
    if (identifiers.isEmpty()) {
      throw new IllegalArgumentException("Namespace must have at least one part");
    }
    Namespace ns = products.get(identifiers.get(0));
    for (Identifier nsName : identifiers.subList(1, identifiers.size())) {
//      checkNamespace(ns, nsName, identifiers);
      if (ns == null) {
        return ns;
      }
      ns = ns.getNamespace(nsName);
    }
//    checkNamespace(ns, identifiers.get(identifiers.size() - 1), identifiers);
    return ns;
  }

  private static void checkNamespace(Namespace namespace, Identifier nsName, List<Identifier> identifiers) {
    if (namespace == null) {
      throw new IllegalArgumentException("Unknown name: " + nsName + " in namespace: " + identifiers);
    }
  }

  @Override
  protected Map<String, Schema> getSubSchemaMap() {
    return products.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().getNormalizedName(),
            Map.Entry::getValue));
  }
}
