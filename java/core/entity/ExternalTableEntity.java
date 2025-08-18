package com.calcite_new.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "external_table_metadata", schema = "public")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalTableEntity {
    @Column(name = "source_product")
    private String sourceProduct;

    @Column(name = "database")
    private String database;

    @Column(name = "schema")
    private String schema;

    @Id
    @Column(name = "external_table_name")
    private String externalTableName;

    @Column(name = "external_table_type")
    private String externalTableType;

    @Column(name = "external_object_name")
    private String externalObjectName;

    @Column(name = "create_at")
    private Long createAt;

    @Column(name = "update_at")
    private Long updateAt;

    @Column(name = "instance")
    private String instance;
}
