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

        // Check if this is a CTE reference - if so, don't qualify or create entities
        String tableName = tableId.getTableName();
        if (tableName != null && scopeManager.hasSubquery(tableName)) {
            log.debug("Table '{}' is a CTE reference, skipping qualification", tableName);
            return tableId; // Return as-is, it's a CTE not a real table
        }

        try {
            // If the table is already fully qualified (3 parts: database.schema.table),
            // just normalize the names and determine entity type
            if (tableId.names.size() == 3) {
                return qualifyFullyQualifiedTable(tableId);
            }

            // For tables that need qualification (1 or 2 parts), use EntityQualifier
            return qualifyPartialTable(tableId);

        } catch (Exception e) {
            log.warn("Failed to qualify table identifier {}: {}", tableId, e.getMessage());
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
        List<Identifier> qualifiedIdentifiers = getQualifiedIdentifiers(tableId);

        // Convert the qualified identifiers back to a list of strings
        List<String> qualifiedNames = new ArrayList<>();
        for (Identifier id : qualifiedIdentifiers) {
            if (id != null) {
                qualifiedNames.add(id.getNormalizedName());
            }
        }

        // Skip dialect prefix for entity resolution
        List<String> entityResolutionNames = qualifiedNames.size() > 1 ?
                qualifiedNames.subList(1, qualifiedNames.size()) : qualifiedNames;

        DatabaseEntity entity = resolveEntity(entityResolutionNames);
        EntityKind entityKind = entity != null ? entity.getKind() : EntityKind.TABLE;

        return createQualifiedIdentifier(entityKind, qualifiedNames, tableId.getParserPosition(), entity);
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
            // Create qualifiers list for EntityResolver
            List<String> qualifiers = new ArrayList<>();

            if (qualifiedNames.size() >= 3) {
                qualifiers.add(qualifiedNames.get(0)); // database
                qualifiers.add(qualifiedNames.get(1)); // schema
                qualifiers.add(qualifiedNames.get(2)); // table/view
            } else if (qualifiedNames.size() == 2) {
                // Add default database if not provided
                if (defaultQualifiers.getDatabase() != null) {
                    qualifiers.add(defaultQualifiers.getDatabase());
                }
                qualifiers.add(qualifiedNames.get(0)); // schema
                qualifiers.add(qualifiedNames.get(1)); // table/view
            } else if (qualifiedNames.size() == 1) {
                // Add default database and schema if not provided
                if (defaultQualifiers.getDatabase() != null) {
                    qualifiers.add(defaultQualifiers.getDatabase());
                }
                if (defaultQualifiers.getSchema() != null) {
                    qualifiers.add(defaultQualifiers.getSchema());
                }
                qualifiers.add(qualifiedNames.get(0)); // table/view
            }

            List<String> defaultQualifiersList = new ArrayList<>();
            EntityQualifier qualifier = new EntityQualifier(qualifiers, defaultQualifiersList, dialect);
            return entityResolver.resolve(qualifier);
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
}




