package com.calcite_new.sql.model.entity.context.clause;

import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderByClause {
    Boolean isOderByInsideSubQuery;
}
