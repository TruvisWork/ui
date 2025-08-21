package com.calcite_new.core.model.entity;

import java.util.List;

/**
 * Interface representing a relational entity, such as a table or view.
 * This interface defines the common behavior for entities that have columns.
 */
public interface RelationalEntity {
  List<Column> getColumns();
}
