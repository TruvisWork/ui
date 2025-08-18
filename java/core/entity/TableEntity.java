package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "table_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableEntity {
    
    @Id
    @Column(name = "table_name")
    private String tableName;
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Column(name = "username")
    private String userName;
    
    @Column(name = "size_mb")
    private BigDecimal sizeMb;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
}