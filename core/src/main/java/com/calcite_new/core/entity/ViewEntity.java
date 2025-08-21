package com.calcite_new.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "view_metadata")
public class ViewEntity {
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Id
    @Column(name = "view_name")
    private String viewName;
    
    @Column(name = "sql_query")
    private String executedSqlQuery;
    
    @Column(name = "view_type")
    private String viewType;
    
    @Column(name = "username")
    private String userName;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
    
    @Column(name = "instance")
    private String instance;
}
