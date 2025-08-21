package com.calcite_new.core.model.entity;

import java.util.List;
import com.calcite_new.core.model.Identifier;

/**
 * Class representing a database external table.
 * An external table is a table that references data stored outside the database system.
 * It extends DatabaseEntity and implements RelationalEntity interface to support column operations.
 *
 * External tables have additional properties compared to regular tables:
 * - externalTableType: The type of external data (e.g., CSV, PARQUET, AVRO)
 * - externalObjectName: The location/path of the external data
 * - sourceProduct: The system hosting the external data (e.g., BIG_QUERY)
 * - instance: Optional instance identifier for the external system
 */
public class ExternalTable extends DatabaseEntity implements RelationalEntity {
    private final String externalTableType;
    private final String externalObjectName;
    private final String sourceProduct;
    private final String instance;
    private final List<Column> columns;

    public ExternalTable(
            List<Identifier> namespace,
            Identifier name,
            List<Column> columns,
            long createAt,
            String externalTableType,
            String externalObjectName,
            String sourceProduct,
            String instance) {
        super(namespace, name, createAt);
        if (columns == null) {
            throw new IllegalArgumentException("columns cannot be null");
        }
        if (externalTableType == null || externalTableType.isEmpty()) {
            throw new IllegalArgumentException("externalTableType cannot be null or empty");
        }
        if (externalObjectName == null || externalObjectName.isEmpty()) {
            throw new IllegalArgumentException("externalObjectName cannot be null or empty");
        }
        if (sourceProduct == null || sourceProduct.isEmpty()) {
            throw new IllegalArgumentException("sourceProduct cannot be null or empty"); 
        }
        this.externalTableType = externalTableType;
        this.externalObjectName = externalObjectName;
        this.sourceProduct = sourceProduct;
        this.instance = instance;
        this.columns = columns;
    }

    @Override
    public EntityKind getKind() {
        return EntityKind.EXTERNAL_TABLE;
    }

    @Override
    public List<Column> getColumns() {
        return columns;
    }


    public String getExternalTableType() {
        return externalTableType;
    }

    public String getExternalObjectName() {
        return externalObjectName;
    }

    public String getSourceProduct() {
        return sourceProduct;
    }

    public String getInstance() {
        return instance;
    }
}
