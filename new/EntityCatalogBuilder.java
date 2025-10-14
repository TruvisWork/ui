package com.calcite_new.core.service;

import com.calcite_new.core.config.RepositoryConfig;
import com.calcite_new.core.entity.ColumnEntity;
import com.calcite_new.core.entity.TableEntity;
import com.calcite_new.core.entity.ViewEntity;
import com.calcite_new.core.entity.ExternalTableEntity;
import com.calcite_new.core.entity.IndicesEntity;
import com.calcite_new.core.entity.PartitionEntity;
import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.Column;
import com.calcite_new.core.model.entity.DataType;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.model.entity.View;
import com.calcite_new.core.model.entity.ExternalTable;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.stream.Collectors;

public class EntityCatalogBuilder {
  private static final Logger logger = Logger.getLogger(EntityCatalogBuilder.class.getName());
    private final RepositoryConfig repositoryConfig;
    private EntityCatalog catalog = null;
    private final BigQuerySqlDialect dialect = new BigQuerySqlDialect();
    private boolean built = false;

    public EntityCatalogBuilder(RepositoryConfig repositoryConfig) {
        this.repositoryConfig = repositoryConfig;
    }

    private List<Column> deduplicateColumns(List<ColumnEntity> columnEntities) {
        Map<String, ColumnEntity> uniqueColumns = new HashMap<>();
        for (ColumnEntity col : columnEntities) {
            if (col.getColumnName() != null && !uniqueColumns.containsKey(col.getColumnName())) {
                uniqueColumns.put(col.getColumnName(), col);
            }
        }
        return uniqueColumns.values().stream()
            .map(this::convertToColumn)
            .collect(Collectors.toList());
    }

    public synchronized EntityCatalog build() {
        if (built && catalog != null) {
            return catalog;
        }
        catalog = new EntityCatalog();
        buildTables();
        buildViews();
        buildExternalTables();
        built = true;
        return catalog;
    }

    private void buildTables() {
        List<TableEntity> tables = repositoryConfig.getTableRepo().fetchAllTables();
        Map<TableKey, List<ColumnEntity>> columnsByTable = getColumnsByTable();
        Map<TableKey, List<String>> clusteredColumnsByTable = getClusteredColumnsByTable();
        Map<TableKey, List<String>> partitionedColumnsByTable = getPartitionedColumnsByTable();

        for (TableEntity tableEntity : tables) {
            TableKey key = new TableKey(
                tableEntity.getDatabase(),
                tableEntity.getSchema(),
                tableEntity.getTableName()
            );

            List<ColumnEntity> columnEntities = columnsByTable.getOrDefault(key, new ArrayList<>());
            List<Column> columns = deduplicateColumns(columnEntities);
            List<String> clusteredColumns = clusteredColumnsByTable.getOrDefault(key, new ArrayList<>());
            List<String> partitionedColumns = partitionedColumnsByTable.getOrDefault(key, new ArrayList<>());

            List<Identifier> namespace = List.of(
                    Identifier.of(dialect.product.name, dialect),
                Identifier.of(tableEntity.getDatabase(), dialect),
                Identifier.of(tableEntity.getSchema(), dialect)
            );

            Identifier tableId = Identifier.of(tableEntity.getTableName(), dialect);
            long timestamp = tableEntity.getCreateAt() != null ? tableEntity.getCreateAt() : 0L;

            Table table = new Table(namespace, tableId, columns, timestamp, clusteredColumns, partitionedColumns);
            try {
                catalog.addEntity(table);
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Failed to add table to catalog: %s.%s.%s - %s",
                    tableEntity.getDatabase(),
                    tableEntity.getSchema(),
                    tableEntity.getTableName(),
                    e.getMessage()));
            }
        }
    }

    private void buildViews() {
        List<ViewEntity> views = repositoryConfig.getViewRepo().fetchAllViews();
        Map<TableKey, List<ColumnEntity>> columnsByView = getColumnsByView();

        for (ViewEntity viewEntity : views) {
            // Skip invalid views (null or empty SQL)
            if (viewEntity.getExecutedSqlQuery() == null || viewEntity.getExecutedSqlQuery().trim().isEmpty()) {
                logger.log(Level.WARNING, String.format("Skipping invalid view: %s.%s.%s due to missing SQL",
                    viewEntity.getDatabase(),
                    viewEntity.getSchema(),
                    viewEntity.getViewName()));
                continue;
            }
            TableKey key = new TableKey(
                viewEntity.getDatabase(),
                viewEntity.getSchema(),
                viewEntity.getViewName()
            );

            List<ColumnEntity> columnEntities = columnsByView.getOrDefault(key, new ArrayList<>());
            List<Column> columns = deduplicateColumns(columnEntities);

            List<Identifier> namespace = List.of(
                    Identifier.of(dialect.product.name, dialect),
                Identifier.of(viewEntity.getDatabase(), dialect),
                Identifier.of(viewEntity.getSchema(), dialect)
            );

            Identifier viewId = Identifier.of(viewEntity.getViewName(), dialect);
            long timestamp = viewEntity.getCreateAt();
            String sqlQuery = viewEntity.getExecutedSqlQuery();

            View view = new View(namespace, viewId, columns, sqlQuery, timestamp);
            try {
                catalog.addEntity(view);
            } catch (Exception e) {
                logger.log(Level.SEVERE, String.format("Failed to add view to catalog: %s.%s.%s - %s",
                    viewEntity.getDatabase(),
                    viewEntity.getSchema(),
                    viewEntity.getViewName(),
                    e.getMessage()));
            }
        }
    }

    private void buildExternalTables() {
        try {
            List<ExternalTableEntity> externalTables = repositoryConfig.getExternalTableRepo().fetchAllExternalTables();
            Map<TableKey, List<ColumnEntity>> columnsByExternalTable = getColumnsByExternalTable();

            for (ExternalTableEntity extTable : externalTables) {
                buildSingleExternalTable(extTable, columnsByExternalTable);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Error building external tables: %s", e.getMessage()));
        }
    }

    private void buildSingleExternalTable(ExternalTableEntity extTable, Map<TableKey, List<ColumnEntity>> columnsByExternalTable) {
        if (!isValidExternalTable(extTable)) {
            return;
        }

        try {
            TableKey key = new TableKey(
                extTable.getDatabase(),
                extTable.getSchema(), 
                extTable.getExternalTableName()
            );
            List<ColumnEntity> columnEntities = columnsByExternalTable.getOrDefault(key, new ArrayList<>());
            List<Column> columns = deduplicateColumns(columnEntities);
            List<Identifier> namespace = List.of(
                    Identifier.of(dialect.product.name, dialect),
                Identifier.of(extTable.getDatabase(), dialect),
                Identifier.of(extTable.getSchema(), dialect)
            );
            Identifier tableId = Identifier.of(extTable.getExternalTableName(), dialect);
            long timestamp = extTable.getCreateAt() != null ? extTable.getCreateAt() : 0L;
            ExternalTable extEntity = new ExternalTable(
                namespace,
                tableId,
                columns,
                timestamp,
                extTable.getExternalTableType(),
                extTable.getExternalObjectName(),
                extTable.getSourceProduct(),
                extTable.getInstance()
            );
            catalog.addEntity(extEntity);
        } catch (Exception e) {
            logger.log(Level.SEVERE, String.format("Error building external table %s.%s.%s: %s",
                extTable.getDatabase(),
                extTable.getSchema(),
                extTable.getExternalTableName(),
                e.getMessage()));
        }
    }

    private boolean isValidExternalTable(ExternalTableEntity extTable) {
        if (extTable == null || 
            extTable.getDatabase() == null || 
            extTable.getSchema() == null || 
            extTable.getExternalTableName() == null || 
            extTable.getExternalTableType() == null ||
            extTable.getExternalObjectName() == null || 
            extTable.getSourceProduct() == null) {
            logger.log(Level.WARNING, String.format("Skipping external table with missing required fields: %s.%s.%s",
                extTable != null ? extTable.getDatabase() : null,
                extTable != null ? extTable.getSchema() : null,
                extTable != null ? extTable.getExternalTableName() : null));
            return false;
        }
        return true;
    }
    private Map<TableKey, List<ColumnEntity>> getColumnsByExternalTable() {
        Map<TableKey, List<ColumnEntity>> columnsByExternalTable = new HashMap<>();
        List<ColumnEntity> allColumns = repositoryConfig.getColumnRepo().fetchAllColumns();
        List<ExternalTableEntity> externalTables = repositoryConfig.getExternalTableRepo().fetchAllExternalTables();

        // Create map of external tables for lookup
        Map<TableKey, ExternalTableEntity> externalTableMap = new HashMap<>();
        for (ExternalTableEntity table : externalTables) {
            if (table.getDatabase() != null && table.getSchema() != null && table.getExternalTableName() != null) {
                TableKey key = new TableKey(
                    normalize(table.getDatabase(), dialect),
                    normalize(table.getSchema(), dialect),
                    normalize(table.getExternalTableName(), dialect)
                );
                if (!externalTableMap.containsKey(key)) {
                    externalTableMap.put(key, table);
                }
            }
        }

        // Map columns to external tables
        for (ColumnEntity column : allColumns) {
            if (isValidColumn(column)) {
                TableKey key = new TableKey(
                    normalize(column.getDatabase(), dialect),
                    normalize(column.getSchema(), dialect),
                    normalize(column.getTable(), dialect)
                );

                if (externalTableMap.containsKey(key)) {
                    columnsByExternalTable
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(column);
                }
            }
        }
        return columnsByExternalTable;
    }

    private Map<TableKey, List<ColumnEntity>> getColumnsByTable() {
        Map<TableKey, List<ColumnEntity>> columnsByTable = new HashMap<>();
        List<ColumnEntity> allColumns = repositoryConfig.getColumnRepo().fetchAllColumns();
        List<TableEntity> tables = repositoryConfig.getTableRepo().fetchAllTables();

        Map<TableKey, TableEntity> tableMap = new HashMap<>();
        for (TableEntity table : tables) {
            TableKey key = new TableKey(
                normalize(table.getDatabase(), dialect),
                normalize(table.getSchema(), dialect),
                normalize(table.getTableName(), dialect)
            );
            if (!tableMap.containsKey(key)) {
                tableMap.put(key, table);
            }
        }

        for (ColumnEntity column : allColumns) {
            if (column.getTable() != null && column.getDatabase() != null && column.getSchema() != null) {
                TableKey key = new TableKey(
                    normalize(column.getDatabase(), dialect),
                    normalize(column.getSchema(), dialect),
                    normalize(column.getTable(), dialect)
                );

                if (tableMap.containsKey(key)) {
                    columnsByTable
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(column);
                }
            }
        }
        return columnsByTable;
    }

    private Map<TableKey, List<ColumnEntity>> getColumnsByView() {
        Map<TableKey, List<ColumnEntity>> columnsByView = new HashMap<>();
        List<ColumnEntity> allColumns = repositoryConfig.getColumnRepo().fetchAllColumns();
        List<ViewEntity> views = repositoryConfig.getViewRepo().fetchAllViews();

        Map<TableKey, ViewEntity> viewMap = views.stream()
            .collect(Collectors.toMap(
                view -> new TableKey(
                    normalize(view.getDatabase(), dialect),
                    normalize(view.getSchema(), dialect),
                    normalize(view.getViewName(), dialect)
                ),
                view -> view,
                (v1, v2) -> v1
            ));

        for (ColumnEntity column : allColumns) {
            if (column.getTable() != null && column.getDatabase() != null && column.getSchema() != null) {
                TableKey key = new TableKey(
                    normalize(column.getDatabase(), dialect),
                    normalize(column.getSchema(), dialect),
                    normalize(column.getTable(), dialect)
                );

                if (viewMap.containsKey(key)) {
                    columnsByView
                        .computeIfAbsent(key, k -> new ArrayList<>())
                        .add(column);
                }
            }
        }
        return columnsByView;
    }

    private Column convertToColumn(ColumnEntity columnEntity) {
        List<Identifier> namespace = new ArrayList<>();
        namespace.add(Identifier.of(columnEntity.getDatabase(), dialect));
        namespace.add(Identifier.of(columnEntity.getSchema(), dialect));
        namespace.add(Identifier.of(columnEntity.getTable(), dialect));
        return  Column.builder()
                .dialect(dialect)
                .name(Identifier.of(columnEntity.getColumnName(), dialect))
                .type(mapDataType(columnEntity))
                .namespace(namespace)
                .ordinalPosition(columnEntity.getColumnPosition().intValue())
                .build();
    }

    private boolean isValidColumn(ColumnEntity column) {
        return column != null && 
               column.getTable() != null && 
               column.getDatabase() != null && 
               column.getSchema() != null && 
               column.getColumnName() != null;
    }

    private Map<TableKey, List<String>> getClusteredColumnsByTable() {
        Map<TableKey, List<String>> clusteredColumnsByTable = new HashMap<>();
        try {
            List<IndicesEntity> allIndices = repositoryConfig.getIndicesRepo().fetchAllIndices();
            
            for (IndicesEntity index : allIndices) {
                if (index.getIndexType() != null && "cluster".equalsIgnoreCase(index.getIndexType())) {
                    if (isValidIndexForClustering(index)) {
                        TableKey key = new TableKey(
                            normalize(index.getDatabase(), dialect),
                            normalize(index.getSchema(), dialect),
                            normalize(index.getIndexName(), dialect)
                        );
                        
                        List<String> columnList = index.getColumnList();
                        if (columnList != null && !columnList.isEmpty()) {
                            clusteredColumnsByTable.put(key, new ArrayList<>(columnList));
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error fetching clustered columns: " + e.getMessage());
        }
        return clusteredColumnsByTable;
    }

    private Map<TableKey, List<String>> getPartitionedColumnsByTable() {
        Map<TableKey, List<String>> partitionedColumnsByTable = new HashMap<>();
        try {
            List<PartitionEntity> allPartitions = repositoryConfig.getPartitionRepo().fetchAllPartitions();
            
            for (PartitionEntity partition : allPartitions) {
                if (isValidPartition(partition)) {
                    TableKey key = new TableKey(
                        normalize(partition.getDatabase(), dialect),
                        normalize(partition.getSchema(), dialect),
                        normalize(partition.getTableName(), dialect)
                    );
                    
                    List<String> partitionColumns = partition.getPartitionColumns();
                    if (partitionColumns != null && !partitionColumns.isEmpty()) {
                        partitionedColumnsByTable.putIfAbsent(key, new ArrayList<>(partitionColumns));
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Error fetching partitioned columns: " + e.getMessage());
        }
        return partitionedColumnsByTable;
    }

    private boolean isValidIndexForClustering(IndicesEntity index) {
        return index != null
            && index.getDatabase() != null
            && index.getSchema() != null
            && index.getIndexName() != null
            && index.getColumnList() != null
            && !index.getColumnList().isEmpty();
    }

    private boolean isValidPartition(PartitionEntity partition) {
        return partition != null
            && partition.getDatabase() != null
            && partition.getSchema() != null
            && partition.getTableName() != null
            && partition.getPartitionColumns() != null
            && !partition.getPartitionColumns().isEmpty();
    }
            
    private DataType mapDataType(ColumnEntity column) {
        String typeName = column.getDataType() != null ? column.getDataType().toUpperCase() : "ANY";
        Long precision = column.getColumnPrecision();
        Long scale = column.getColumnScale();

        try {
            SqlTypeName sqlType = SqlTypeName.valueOf(typeName);
            boolean hasPrec = sqlType.allowsPrec();
            boolean hasScale = sqlType.allowsScale();
            int prec = precision != null ? precision.intValue() : -1;
            int sc = scale != null ? scale.intValue() : -1;

            if (hasPrec && hasScale) {
                return DataType.create(sqlType, prec, sc);
            } else if (hasPrec) {
                return DataType.create(sqlType, prec);
            } else if (hasScale) {
                return DataType.create(sqlType, column.getNullable() != null ? column.getNullable() : true);
            } else {
                return DataType.create(sqlType, column.getNullable() != null ? column.getNullable() : true);
            }
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, String.format("Unmapped data type: %s, using ANY", typeName));
            return DataType.create(SqlTypeName.ANY, -1, -1, column.getNullable() != null ? column.getNullable() : true);
        }
    }

    public EntityCatalogBuilder withCatalog(EntityCatalog catalog) {
        this.catalog = catalog;
        return this;
    }

    private static String normalize(String s, BigQuerySqlDialect dialect) {
        if (s == null) return null;
        return Identifier.of(s, dialect).getNormalizedName();
    }

    private static class TableKey {
        private final String database;
        private final String schema;
        private final String name;

        public TableKey(String database, String schema, String name) {
            this.database = database != null ? database : "";
            this.schema = schema != null ? schema : "";
            this.name = name != null ? name : "";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TableKey tableKey = (TableKey) o;
            return Objects.equals(database, tableKey.database) &&
                    Objects.equals(schema, tableKey.schema) &&
                    Objects.equals(name, tableKey.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(database, schema, name);
        }
    }
}