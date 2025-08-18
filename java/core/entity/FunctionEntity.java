package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "function_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FunctionEntity {
    
    @Id
    @Column(name = "function_name")
    private String functionName;
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Column(name = "sql_query", columnDefinition = "TEXT")
    private String ExecutedSqlQuery;
    
    @Column(name = "user_name")
    private String userName;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
    
    @Column(name = "instance")
    private String instance;
}
