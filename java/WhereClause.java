package com.calcite_new.sql.model.entity.context.clause;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhereClause {
    private Boolean hasTrueCondition;
    private Boolean hasCaseInsensitiveComparison;
    private Boolean hasInWithSubquery;
    private Boolean hasInWithConstant;
    private Boolean hasWhereClause;
    private Boolean hasFunctionOnPartitionColumn;
    private Boolean hasUnusedWindowFunction;
}
