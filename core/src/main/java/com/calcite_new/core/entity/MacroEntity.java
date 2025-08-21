package com.calcite_new.core.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "macro_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MacroEntity {
    
    @Column(name = "database")
    private String database;
    
    @Column(name = "schema")
    private String schema;
    
    @Id
    @Column(name = "macro_name")
    private String macroName;
    
    @Column(name = "user_name")
    private String userName;
    
    @Column(name = "sql_query", columnDefinition = "TEXT")
    private String executedSqlQuery;
    
    @Column(name = "create_at")
    private Long createAt;
    
    @Column(name = "update_at")
    private Long updateAt;
    
    @Column(name = "instance")
    private String instance;
}
