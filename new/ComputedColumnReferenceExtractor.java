package com.calcite_new.sql.core.processor.visitor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.util.SqlBasicVisitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to extract computed column references from an enriched SQL tree.
 * This helps identify where computed columns (window functions, aggregates, expressions)
 * are referenced throughout the query.
 * }</pre>
 */
@Slf4j
public class ComputedColumnReferenceExtractor {

    @Getter
    private final List<ComputedColumnReference> references = new ArrayList<>();

    @Getter
    private final Map<String, List<ComputedColumnReference>> referencesByAlias = new HashMap<>();

    /**
     * Extracts all computed column references from the enriched SQL tree.
     */
    public void extract(SqlNode enrichedNode) {
        if (enrichedNode == null) {
            return;
        }

        references.clear();
        referencesByAlias.clear();

        enrichedNode.accept(new ComputedColumnVisitor());

        log.info("Extracted {} computed column references", references.size());
    }

    /**
     * Gets all references for a specific computed column alias.
     */
    public List<ComputedColumnReference> getReferencesForAlias(String alias) {
        return referencesByAlias.getOrDefault(alias.toLowerCase(), new ArrayList<>());
    }

    /**
     * Checks if a computed column is referenced in the query.
     */
    public boolean hasReferences(String alias) {
        return referencesByAlias.containsKey(alias.toLowerCase());
    }

    /**
     * Visitor that finds all SqlComputedColumnIdentifier instances.
     */
    private class ComputedColumnVisitor extends SqlBasicVisitor<Void> {
        private String currentContext = "UNKNOWN";

        @Override
        public Void visit(SqlIdentifier id) {
            if (id instanceof SqlComputedColumnIdentifier computedCol) {
                ComputedColumnReference ref = new ComputedColumnReference(
                        computedCol.getAliasName(),
                        computedCol.getSourceExpression(),
                        computedCol.isWindowFunction(),
                        computedCol.isAggregateFunction(),
                        currentContext,
                        computedCol
                );

                references.add(ref);

                // Add to alias map
                String aliasKey = computedCol.getAliasName().toLowerCase();
                referencesByAlias.computeIfAbsent(aliasKey, k -> new ArrayList<>()).add(ref);

                log.debug("Found computed column reference: {} in {}",
                        computedCol.getAliasName(), currentContext);
            }
            return null;
        }
    }

    /**
     * Represents a single reference to a computed column.
     */
    @Getter
    public static class ComputedColumnReference {
        private final String aliasName;
        private final SqlNode sourceExpression;
        private final boolean isWindowFunction;
        private final boolean isAggregateFunction;
        private final String context;
        private final SqlComputedColumnIdentifier identifier;

        public ComputedColumnReference(
                String aliasName,
                SqlNode sourceExpression,
                boolean isWindowFunction,
                boolean isAggregateFunction,
                String context,
                SqlComputedColumnIdentifier identifier) {
            this.aliasName = aliasName;
            this.sourceExpression = sourceExpression;
            this.isWindowFunction = isWindowFunction;
            this.isAggregateFunction = isAggregateFunction;
            this.context = context;
            this.identifier = identifier;
        }

        @Override
        public String toString() {
            return String.format("ComputedColumnReference{alias='%s', context='%s', isWindow=%s, isAggregate=%s, source=%s}",
                    aliasName, context, isWindowFunction, isAggregateFunction,
                    sourceExpression != null ? sourceExpression.toString() : "null");
        }
    }
}

