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
import java.util.Arrays;
import java.util.ArrayList;


public class RelationshipExtractor {
    private static final Logger log = LoggerFactory.getLogger(RelationshipExtractor.class);
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

    public void createJoin(SqlIdentifier leftId, SqlIdentifier rightId, String defaultDb, String defaultSchema, List<EntityRelationship> relations) {
        if (leftId == null || rightId == null) {
            throw new NullPointerException("Neither left nor right identifier can be null");
        }
        if (relations == null) {
            throw new NullPointerException("Relations list cannot be null");
        }
        try {
            Entity left = createEntity(leftId, defaultDb, defaultSchema);
            Entity right = createEntity(rightId, defaultDb, defaultSchema);
            if (left.equals(right)) {
                return;
            }
            if (relationDoesNotExist(left, right, RelationshipType.JOINS, relations)) {
                relations.add(EntityRelationship.builder()
                        .relationshipType(RelationshipType.JOINS)
                        .sourceEntity(left)
                        .targetEntity(right)
                        .build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to create join relationship", e);
        }
    }

    private boolean relationDoesNotExist(Entity source, Entity target, RelationshipType type, List<EntityRelationship> relations) {
        if (relations == null) return true;
        return relations.stream().noneMatch(rel -> {
            if (rel.getRelationshipType() != type) {
                return false;
            }
            boolean directMatch = Objects.equals(rel.getSourceEntity(), source) && Objects.equals(rel.getTargetEntity(), target);
            if (type == RelationshipType.JOINS) {
                boolean reverseMatch = Objects.equals(rel.getSourceEntity(), target) && Objects.equals(rel.getTargetEntity(), source);
                return directMatch || reverseMatch;
            }
            return directMatch;
        });
    }

    private EntityType determineEntityType(String normalizedDb, String normalizedSchema, String normalizedTable) {
        if (normalizedDb == null || normalizedSchema == null || normalizedTable == null) {
            return EntityType.TABLE;
        }
        try {
            List<String> qualifiers = createQualifiers(normalizedDb, normalizedSchema, normalizedTable);
            List<String> defaultQualifiers = Arrays.asList(
                    normalizedDb != null ? normalizedDb : "default_db",
                    normalizedSchema != null ? normalizedSchema : "default_schema"
            );
            EntityQualifier qualifier = new EntityQualifier(qualifiers, defaultQualifiers, dialect);
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
            return EntityType.TABLE;
        }catch (Exception e) {
            return EntityType.TABLE;
        }
    }

    private static List<String> createQualifiers(String db, String schema, String table) {
        List<String> qualifiers = new ArrayList<>();
        if (db != null) qualifiers.add(db);
        if (schema != null) qualifiers.add(schema);
        if (table != null) qualifiers.add(table);
        return qualifiers;
    }
}

