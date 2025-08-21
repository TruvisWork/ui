package com.calcite_new.sql.relationextractor;

import com.calcite_new.core.dialect.sql.BigQuerySqlDialect;
import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.model.entity.EntityRelationship;
import org.apache.calcite.sql.SqlIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;


public class RelationshipExtractor {
    private static final Logger log = LoggerFactory.getLogger(RelationshipExtractor.class);
    private final EntityResolver entityResolver;
    private final BigQuerySqlDialect dialect;

    public RelationshipExtractor(EntityResolver entityResolver, BigQuerySqlDialect dialect) {
        log.error("TEST - Creating RelationshipExtractor with resolver: {}", entityResolver);
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

        log.info("Creating entity for: {}.{}.{}", normalizedDb, normalizedSchema, normalizedTable);
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
            if (relationDoesNotExist(userEntity, tableEntity, RelationshipType.ACCESSES, relations)) {
                relations.add(EntityRelationship.builder()
                        .relationshipType(RelationshipType.ACCESSES)
                        .sourceEntity(userEntity)
                        .targetEntity(tableEntity)
                        .build());
            }
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
            if (relationDoesNotExist(target, source, RelationshipType.DEPENDS_ON, relations)) {
                relations.add(EntityRelationship.builder()
                        .relationshipType(RelationshipType.DEPENDS_ON)
                        .sourceEntity(target)
                        .targetEntity(source)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create depends-on relationship", e);
        }
    }

    private boolean relationDoesNotExist(Entity source, Entity target, RelationshipType type, List<EntityRelationship> relations) {
        if (relations == null) return true;
        return relations.stream().noneMatch(rel ->
                rel.getRelationshipType() == type &&
                        Objects.equals(rel.getSourceEntity(), source) &&
                        Objects.equals(rel.getTargetEntity(), target)
        );
    }

    private EntityType determineEntityType(String normalizedDb, String normalizedSchema, String normalizedTable) {
        log.info("Called determineEntityType for: {}.{}.{}", normalizedDb, normalizedSchema, normalizedTable);
        if (normalizedDb == null || normalizedSchema == null || normalizedTable == null) {
            return EntityType.TABLE;
        }
        try {
            List<String> qualifiers = List.of(normalizedTable);
            List<String> namespaces = List.of(normalizedDb, normalizedSchema);
            log.info("About to create qualifier with qualifiers: {}, namespaces: {}", qualifiers, namespaces);
            EntityQualifier qualifier = new EntityQualifier(qualifiers, namespaces, dialect);
            log.info("About to resolve entity using resolver: {}", entityResolver);
            DatabaseEntity entity = entityResolver.resolve(qualifier);
            log.info("Resolver returned entity: {}", entity);
            if (entity == null) {
                return EntityType.TABLE;
            }
            log.info("Entity {}.{}.{} resolved with kind: {} and metadata: {}", 
                normalizedDb, normalizedSchema, normalizedTable, entity.getKind(), entity);
            switch (entity.getKind()) {
                case VIEW:
                    log.info("Entity {}.{}.{} resolved as VIEW", normalizedDb, normalizedSchema, normalizedTable);
                    return EntityType.VIEW;
                case EXTERNAL_TABLE:
                    log.info("Entity {}.{}.{} resolved as EXTERNAL_TABLE", normalizedDb, normalizedSchema, normalizedTable);
                    return EntityType.EXTERNAL_TABLE;
                default:
                    log.info("Entity {}.{}.{} resolved as TABLE", normalizedDb, normalizedSchema, normalizedTable);
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

