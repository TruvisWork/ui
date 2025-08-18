package com.calcite_new.sql.relationextractor;

import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.model.entity.EntityRelationship;
import org.apache.calcite.sql.SqlIdentifier;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class RelationshipExtractor {
    private final EntityResolver entityResolver;
    private final BigQuerySqlDialect dialect;

    public RelationshipExtractor(EntityResolver entityResolver, BigQuerySqlDialect dialect) {
        this.entityResolver = entityResolver;
        this.dialect = dialect;
    }

    public Entity createEntity(SqlIdentifier identifier, String defaultDb, String defaultSchema) {
        if (identifier == null || identifier.names.isEmpty()) {
            throw new IllegalArgumentException("Identifier cannot be null or empty");
        }

        String db = identifier.names.size() == 3 ? identifier.names.get(0) : defaultDb;
        String schema = identifier.names.size() >= 2 ? identifier.names.get(identifier.names.size() - 2) : defaultSchema;
        String table = identifier.names.get(identifier.names.size() - 1);

        Identifier dbIdentifier = db != null ? Identifier.of(db, dialect) : null;
        Identifier schemaIdentifier = schema != null ? Identifier.of(schema, dialect) : null;
        Identifier tableIdentifier = table != null ? Identifier.of(table, dialect) : null;

        String normalizedDb = dbIdentifier != null ? dbIdentifier.getNormalizedName() : null;
        String normalizedSchema = schemaIdentifier != null ? schemaIdentifier.getNormalizedName() : null;
        String normalizedTable = tableIdentifier != null ? tableIdentifier.getNormalizedName() : null;

        EntityType entityType = determineEntityType(normalizedDb, normalizedSchema, normalizedTable);

        return Entity.builder()
                .database(normalizedDb)
                .schema(normalizedSchema)
                .entityName(normalizedTable)
                .entityType(entityType)
                .build();
    }

    public void createAccess(SqlIdentifier identifier, String userName, String defaultDb, String defaultSchema, List<EntityRelationship> relations) {
        if (identifier == null) {
            throw new NullPointerException("Identifier cannot be null");
        }
        if (userName == null || userName.trim().isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty");
        }
        if (relations == null) {
            throw new NullPointerException("Relations list cannot be null");
        }
        try {
            Entity tableEntity = createEntity(identifier, defaultDb, defaultSchema);
            Entity userEntity = Entity.builder()
                    .entityName(userName)
                    .entityType(EntityType.USER)
                    .build();
            relations.add(EntityRelationship.builder()
                    .relationshipType(RelationshipType.ACCESSES)
                    .sourceEntity(userEntity)
                    .targetEntity(tableEntity)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create access relationship", e);
        }
    }

    public void createDependsOn(SqlIdentifier targetId, SqlIdentifier sourceId, String defaultDb, String defaultSchema, List<EntityRelationship> relations) {
        if (targetId == null || sourceId == null) {
            throw new NullPointerException("Neither target nor source identifier can be null");
        }
        if (relations == null) {
            throw new NullPointerException("Relations list cannot be null");
        }
        try {
            Entity target = createEntity(targetId, defaultDb, defaultSchema);
            Entity source = createEntity(sourceId, defaultDb, defaultSchema);
            if (target.equals(source)) {
                // Self-dependency is skipped
                return;
            }
            relations.add(EntityRelationship.builder()
                    .relationshipType(RelationshipType.DEPENDS_ON)
                    .sourceEntity(target)
                    .targetEntity(source)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to create depends-on relationship", e);
        }
    }

    private EntityType determineEntityType(String normalizedDb, String normalizedSchema, String normalizedTable) {
        if (normalizedDb == null || normalizedSchema == null || normalizedTable == null) {
            return EntityType.TABLE;
        }
        try {
            List<String> qualifiers = List.of(normalizedTable);
            List<String> namespaces = List.of(normalizedDb, normalizedSchema);
            EntityQualifier qualifier = new EntityQualifier(qualifiers, namespaces, dialect);
            DatabaseEntity entity = entityResolver.resolve(qualifier);
            if (entity == null) {
                return EntityType.TABLE;
            }
            switch (entity.getKind()) {
                case VIEW:
                    return EntityType.VIEW;
                case EXTERNAL_TABLE:
                    return EntityType.EXTERNAL_TABLE;
                default:
                    return EntityType.TABLE;
            }
        } catch (IllegalArgumentException e) {
            log.debug("Entity not found in catalog, defaulting to TABLE: {}.{}.{}",
                    normalizedDb, normalizedSchema, normalizedTable);
            return EntityType.TABLE;
        }catch (Exception e) {
            log.error("Error determining entity type for {}.{}.{}: {}",
                    normalizedDb, normalizedSchema, normalizedTable, e.getMessage());
            return EntityType.TABLE;
        }
    }

}

