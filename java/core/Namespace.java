package com.calcite_new.core.model;

import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.impl.AbstractSchema;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;

/**
 * Namespace is a schema that contains a collection of database entities and child namespaces.
 * It is used to represent a database schema in the EntityCatalog.
 */
public class Namespace extends AbstractSchema {
  private final Identifier name;
  private final Map<Identifier, Namespace> children = new ConcurrentHashMap<>();
  private final EntityMap<Table> tables = new EntityMap<>();
  private final EntityMap<View> views = new EntityMap<>();

  public Namespace(Identifier name) {
    this.name = name;
  }

  public Identifier getName() {
    return name;
  }

  public void addTable(Table table) {
    tables.put(table);
  }

  public void addView(View view) {
    views.put(view);
  }

  public Table getTable(Identifier name) {
    return tables.get(name);
  }

  public View getView(Identifier name) {
    return views.get(name);
  }

  public Namespace getNamespace(Identifier name) {
    return children.get(name);
  }

  Namespace getOrAddNamespace(Identifier name) {
    return children.computeIfAbsent(name, Namespace::new);
  }

  @Override
  protected Map<String, Schema> getSubSchemaMap() {
    // convert children to a map of string to schema
    return children.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().getNormalizedName(),
            Map.Entry::getValue));
  }

  @Override
  protected Map<String, org.apache.calcite.schema.Table> getTableMap() {
    // return a map of string to table
    return tables.entities.entrySet().stream()
        .collect(Collectors.toMap(
            entry -> entry.getKey().name.getNormalizedName(),
            entry -> ScannableTable.create(entry.getValue())));
  }

  private record Key(Identifier name, long createdTimestamp) implements Comparable<Key> {

    @Override
    public int compareTo(Key other) {
      int cmp = name.getNormalizedName().compareTo(other.name.getNormalizedName());
      if (cmp != 0) {
        return cmp;
      }
      return Long.compare(createdTimestamp, other.createdTimestamp);
    }

  }

  private static class EntityMap<V extends DatabaseEntity> {
    private final NavigableMap<Key, V> entities = new ConcurrentSkipListMap<>();

    public V get(Identifier name) {
      Key key = new Key(name, System.currentTimeMillis());
      Map.Entry<Key, V> entry = entities.floorEntry(key);
      if (entry.getKey().name.equals(name)) {
        return entry.getValue();
      }
      return null;
    }

    public void put(V entity) {
      Key key = new Key(entity.getName(), entity.getCreatedTimestamp());
      entities.put(key, entity);
    }
  }
}
