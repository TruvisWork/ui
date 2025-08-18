package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

@Entity
@Table(name = "indices_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicesEntity {
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Id
    @Column(name = "index_name")
    private String indexName;
    
    @Column(name = "user_name")
    private String userName;
    
    @Column(name = "column_list")
    private List<String> columnList;
    
    @Column(name = "index_identifier")
    private String indexIdentifier;
    
    @Column(name = "index_type")
    private String indexType;
    
    @Column(name = "ddl_statement", columnDefinition = "TEXT")
    private String ddlStatement;
    
    @Column(name = "is_unique")
    private Boolean isUnique;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
    
    @Column(name = "instance")
    private String instance;
}
