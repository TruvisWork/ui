package com.calcite_new.sql.core.processor.visitor;

import lombok.Getter;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.Collections;

/**
 * Represents an ordinal reference in GROUP BY or ORDER BY clauses.
 * Example: SELECT customer_id, SUM(sales) FROM t GROUP BY 1
 * The "1" is an ordinal reference to the first item in the SELECT list.
 */
@Getter
public class SqlOrdinalReference extends SqlIdentifier {

    private final int ordinalPosition;
    private final SqlNode referencedSelectItem;
    private final OrdinalContext context;

    public enum OrdinalContext {
        GROUP_BY,
        ORDER_BY
    }

    public SqlOrdinalReference(
            int ordinalPosition,
            SqlNode referencedSelectItem,
            OrdinalContext context,
            SqlParserPos pos) {
        super(Collections.singletonList(String.valueOf(ordinalPosition)), pos);
        this.ordinalPosition = ordinalPosition;
        this.referencedSelectItem = referencedSelectItem;
        this.context = context;
    }

    @Override
    public String toString() {
        return String.format("SqlOrdinalReference(%d -> %s, context: %s)",
                ordinalPosition,
                referencedSelectItem != null ? referencedSelectItem.toString() : "null",
                context);
    }
}

