package com.calcite_new.sql.model.entity.context.clause;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.List;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelectClause {

    private Boolean hasSelectAll;
    private Boolean hasDistinct;
    private List<String> unusedColumns;

}
