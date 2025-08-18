package com.calcite_new.sql.model.entity;

import com.calcite_new.sql.relationextractor.RelationshipType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "entity_relationship")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
@EqualsAndHashCode(of = {"sourceEntity", "targetEntity", "relationshipType"})
public class EntityRelationship {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "database", column = @Column(name = "source_database")),
            @AttributeOverride(name = "schema", column = @Column(name = "source_schema")),
            @AttributeOverride(name = "entityName", column = @Column(name = "source_entity_name")),
            @AttributeOverride(name = "entityType", column = @Column(name = "source_entity_type")),
            @AttributeOverride(name = "location", column = @Column(name = "source_location"))
    })
    private com.calcite_new.sql.relationextractor.Entity sourceEntity;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "database", column = @Column(name = "target_database")),
            @AttributeOverride(name = "schema", column = @Column(name = "target_schema")),
            @AttributeOverride(name = "entityName", column = @Column(name = "target_entity_name")),
            @AttributeOverride(name = "entityType", column = @Column(name = "target_entity_type")),
            @AttributeOverride(name = "location", column = @Column(name = "target_location"))
    })
    private com.calcite_new.sql.relationextractor.Entity targetEntity;

    @Enumerated(EnumType.STRING)
    private RelationshipType relationshipType;

    @ManyToOne
    @JoinColumn(name = "sql_statement_info_id")
    private SqlStatementInfo sqlStatementInfo;

}