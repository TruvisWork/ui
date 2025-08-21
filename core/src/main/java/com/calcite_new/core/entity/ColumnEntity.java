package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "column_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnEntity {
    
    @Id
    @Column(name = "column_name")
    private String columnName;
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Column(name = "\"table\"")
    private String table;
    
    @Column(name = "username")
    private String userName;
    
    @Column(name = "column_position")
    private Long columnPosition;
    
    @Column(name = "data_type")
    private String dataType;
    
    @Column(name = "column_length")
    private Long columnLength;
    
    @Column(name = "column_precision")
    private Long columnPrecision;
    
    @Column(name = "column_scale")
    private Long columnScale;
    
    @Column(name = "column_format")
    private String columnFormat;
    
    @Column(name = "default_value")
    private String defaultValue;
    
    @Column(name = "nullable")
    private Boolean nullable;
    
    @Column(name = "is_uppercase")
    private Boolean isUppercase;
    
    @Column(name = "is_case_sensitive")
    private Boolean isCaseSensitive;
    
    @Column(name = "column_constraint")
    private String columnConstraint;
    
    @Column(name = "compressible")
    private Boolean compressible;
    
    @Column(name = "compress_value_list")
    private String compressValueList;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
    
    @Column(name = "instance")
    private String instance;
}