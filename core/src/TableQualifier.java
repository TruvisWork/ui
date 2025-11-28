package com.calcite_new.sql.core.processor.visitor.qualifier;

import com.calcite_new.core.model.EntityQualifier;
import com.calcite_new.core.model.Identifier;
import com.calcite_new.core.model.entity.EntityKind;
import com.calcite_new.core.model.entity.DatabaseEntity;
import com.calcite_new.core.model.entity.Table;
import com.calcite_new.core.dialect.sql.SqlDialect;
import com.calcite_new.core.resolver.EntityResolver;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.core.processor.DefaultQualifiers;
import com.calcite_new.sql.core.processor.visitor.scope.ScopeManager;
import com.calcite_new.sql.core.processor.visitor.scope.SubqueryInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Handles qualification of table and view identifiers.
 * Resolves unqualified table references to fully qualified identifiers with entity information.
 */
@Slf4j
public class TableQualifier {

    private final DefaultQualifiers defaultQualifiers;
    private final SqlDialect dialect;
    private final EntityResolver entityResolver;
    private final ScopeManager scopeManager;

    public TableQualifier(DefaultQualifiers defaultQualifiers,
                          SqlDialect dialect,
                          EntityResolver entityResolver,
                          ScopeManager scopeManager) {
        this.defaultQualifiers = Objects.requireNonNull(defaultQualifiers, "defaultQualifiers cannot be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect cannot be null");
        this.entityResolver = Objects.requireNonNull(entityResolver, "entityResolver cannot be null");
        this.scopeManager = Objects.requireNonNull(scopeManager, "scopeManager cannot be null");
    }

    /**
     * Qualifies a SqlTableIdentifier using EntityResolver to determine if it's a table or view,
     * and returns the appropriate qualified identifier with the entity attached.
     */
    public SqlIdentifier qualifyTableIdentifier(SqlTableIdentifier tableId) {
        if (tableId == null || tableId.names == null) {
            return tableId;
        }

        log.debug("Qualifying table identifier: {} with names: {}", tableId, tableId.names);

        // Check if this is a CTE reference - if so, create a virtual entity for it
        String tableName = tableId.getTableName();
        if (tableName != null && scopeManager.hasSubquery(tableName)) {
            log.debug("Table '{}' is a CTE reference, creating virtual entity", tableName);
            return createVirtualEntityForCte(tableId, tableName);
        }

        try {
            // If the table is already fully qualified (3 parts: database.schema.table),
            // just normalize the names and determine entity type
            if (tableId.names.size() == 3) {
                log.debug("Table has 3 parts, treating as fully qualified");
                return qualifyFullyQualifiedTable(tableId);
            }

            // For tables that need qualification (1 or 2 parts), use EntityQualifier
            log.debug("Table has {} parts, treating as partially qualified", tableId.names.size());
            SqlIdentifier result = qualifyPartialTable(tableId);
            log.debug("Qualified result: {} with entity: {}", result,
                    result instanceof SqlTableIdentifier ? ((SqlTableIdentifier) result).getEntity() : "N/A");
            return result;

        } catch (Exception e) {
            // If qualification fails, it might be because this is a CTE reference
            // that's not yet registered in the scope
            log.debug("Failed to qualify table identifier {}: {}. Treating as potential CTE reference.", tableId, e.getMessage());
            return tableId; // Return original if qualification failed
        }
    }

    /**
     * Qualifies a fully qualified table identifier (database.schema.table).
     */
    private SqlIdentifier qualifyFullyQualifiedTable(SqlTableIdentifier tableId) {
        List<String> normalizedNames = normalizeNames(tableId.names);

        // Determine if it's a table or view using EntityResolver and get the entity
        DatabaseEntity entity = resolveEntity(normalizedNames);
        EntityKind entityKind = entity != null ? entity.getKind() : EntityKind.TABLE;

        return createQualifiedIdentifier(entityKind, normalizedNames, tableId.getParserPosition(), entity);
    }

    /**
     * Qualifies a partially qualified table identifier (table or schema.table).
     */
    private SqlIdentifier qualifyPartialTable(SqlTableIdentifier tableId) {
        log.debug("Qualifying partial table: {} with names: {}", tableId, tableId.names);

        List<Identifier> qualifiedIdentifiers = getQualifiedIdentifiers(tableId);
        log.debug("Got qualified identifiers: {}", qualifiedIdentifiers);

        // Convert the qualified identifiers back to a list of strings
        List<String> qualifiedNames = new ArrayList<>();
        for (Identifier id : qualifiedIdentifiers) {
            if (id != null) {
                qualifiedNames.add(id.getNormalizedName());
            }
        }
        log.debug("Qualified names: {}", qualifiedNames);

        // Skip dialect prefix for entity resolution
        List<String> entityResolutionNames = qualifiedNames.size() > 1 ?
                qualifiedNames.subList(1, qualifiedNames.size()) : qualifiedNames;
        log.debug("Entity resolution names: {}", entityResolutionNames);

        DatabaseEntity entity = resolveEntity(entityResolutionNames);
        log.debug("Resolved entity: {}", entity);

        EntityKind entityKind = entity != null ? entity.getKind() : EntityKind.TABLE;
        log.debug("Entity kind: {}", entityKind);

        SqlIdentifier result = createQualifiedIdentifier(entityKind, qualifiedNames, tableId.getParserPosition(), entity);
        log.debug("Created qualified identifier: {} with entity: {}", result,
                result instanceof SqlTableIdentifier ? ((SqlTableIdentifier) result).getEntity() : "N/A");
        return result;
    }

    /**
     * Gets qualified identifiers for a table identifier.
     */
    private List<Identifier> getQualifiedIdentifiers(SqlTableIdentifier tableId) {
        List<String> qualifiers = new ArrayList<>();
        if (tableId.getDatabaseName() != null) {
            qualifiers.add(tableId.getDatabaseName());
        }
        if (tableId.getSchemaName() != null) {
            qualifiers.add(tableId.getSchemaName());
        }
        qualifiers.add(tableId.getTableName());

        EntityQualifier entityQualifier = getEntityQualifier(qualifiers);
        return entityQualifier.getQualifiers();
    }

    /**
     * Creates an EntityQualifier for the given qualifiers.
     */
    private EntityQualifier getEntityQualifier(List<String> qualifiers) {
        List<String> defaultQualifiersList = new ArrayList<>();
        if (defaultQualifiers.getDatabase() != null) {
            defaultQualifiersList.add(defaultQualifiers.getDatabase());
        }
        if (defaultQualifiers.getSchema() != null) {
            defaultQualifiersList.add(defaultQualifiers.getSchema());
        }

        return new EntityQualifier(qualifiers, defaultQualifiersList, dialect);
    }

    /**
     * Resolves the entity using EntityResolver and returns the DatabaseEntity.
     */
    private DatabaseEntity resolveEntity(List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return null;
        }

        try {
            // Use the proper getEntityQualifier method to construct qualifiers correctly
            EntityQualifier entityQualifier = getEntityQualifier(qualifiedNames);

            log.debug("Resolving entity for qualifiedNames: {}, EntityQualifier qualifiers: {}",
                    qualifiedNames, entityQualifier.getQualifiers());

            // Resolve the entity using EntityResolver
            DatabaseEntity entity = entityResolver.resolve(entityQualifier);

            log.debug("Resolved entity: {}", entity != null ? entity.getName() : "null");

            if (entity != null) {
                return entity;
            } else {
                log.debug("Entity not found in catalog: {}", qualifiedNames);
                return createDefaultEntity(qualifiedNames);
            }
        } catch (Exception e) {
            log.debug("Failed to resolve entity for {}: {}", qualifiedNames, e.getMessage());
            // If resolution fails, create a default entity (table by default)
            return createDefaultEntity(qualifiedNames);
        }
    }

    /**
     * Creates a default entity when resolution fails.
     */
    private DatabaseEntity createDefaultEntity(List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return null;
        }

        String tableName = qualifiedNames.get(qualifiedNames.size() - 1);
        List<Identifier> namespace = new ArrayList<>();

        for (int i = 0; i < qualifiedNames.size() - 1; i++) {
            namespace.add(Identifier.of(qualifiedNames.get(i), dialect));
        }

        return new Table(
                namespace,
                Identifier.of(tableName, dialect),
                new ArrayList<>(),
                System.currentTimeMillis()
        );
    }

    /**
     * Creates the appropriate qualified identifier based on entity kind.
     */
    private SqlIdentifier createQualifiedIdentifier(EntityKind entityKind,
                                                    List<String> qualifiedNames,
                                                    SqlParserPos pos,
                                                    DatabaseEntity entity) {
        if (entityKind == EntityKind.VIEW) {
            return new SqlViewIdentifier(qualifiedNames, pos, entity);
        } else {
            return new SqlTableIdentifier(qualifiedNames, pos, entity);
        }
    }

    /**
     * Normalizes identifier names using the dialect.
     */
    private List<String> normalizeNames(List<String> names) {
        List<String> normalizedNames = new ArrayList<>();
        for (String name : names) {
            if (name != null) {
                String normalizedName = Identifier.of(name, dialect).getNormalizedName();
                normalizedNames.add(normalizedName);
            }
        }
        return normalizedNames;
    }

    /**
     * Creates a virtual entity for a CTE reference.
     * For CTEs, we typically have one source table, so we just use the first available source entity.
     */
    private SqlIdentifier createVirtualEntityForCte(SqlTableIdentifier tableId, String cteName) {
        SubqueryInfo cteInfo = scopeManager.getSubqueryInfo(cteName);
        if (cteInfo == null) {
            log.debug("No CTE info found for '{}', returning original identifier", cteName);
            return tableId;
        }

        // For CTEs, typically there's one source table - just use the first available source entity
        DatabaseEntity sourceEntity = findFirstSourceEntity(cteInfo);

        if (sourceEntity != null) {
            log.debug("Creating virtual entity for CTE '{}' based on source entity '{}'",
                    cteName, sourceEntity.getName());

            // Create a virtual table identifier with the source entity attached
            return new SqlTableIdentifier(
                    tableId.names,
                    tableId.getParserPosition(),
                    sourceEntity
            );
        } else {
            log.debug("No source entity found for CTE '{}', returning original identifier", cteName);
            return tableId;
        }
    }

    /**
     * Finds the first available source entity from a CTE's column projections.
     * For most CTEs, there's typically one source table.
     */
    private DatabaseEntity findFirstSourceEntity(SubqueryInfo cteInfo) {
        for (SubqueryInfo.ColumnProjection projection : cteInfo.getColumnProjections()) {
            DatabaseEntity sourceEntity = projection.getSourceEntity();
            if (sourceEntity != null) {
                return sourceEntity;
            }
        }
        return null;
    }
}