package com.calcite_new.core.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "partition_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartitionEntity {
    
    @Column(name = "source_product")
    private String sourceProduct;
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Column(name = "table_name")
    private String tableName;
    
    @Id
    @Column(name = "partition_name")
    private String partitionName;
    
    @Column(name = "partition_columns")
    private List<String> partitionColumns;
    
    @Column(name = "partition_text")
    private String partitionText;
    
    @Column(name = "user_name")
    private String userName;
    
    @Column(name = "partition_size_bytes")
    private Long partitionSizeBytes;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "instance")
    private String instance;
}
