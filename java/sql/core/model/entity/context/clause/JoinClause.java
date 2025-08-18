package com.calcite_new.sql.model.entity.context.clause;

import jakarta.persistence.Embeddable;
import lombok.*;
import org.apache.calcite.sql.JoinType;

import java.util.List;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinClause {
/*    private Entity leftTable;
    private Entity rightTable;*/
    private JoinType joinType;
    private Boolean isEquiJoin;
    private String joinCondition;
    private Boolean hasJoinOnStringColumn;
    private List<String> joinTables;
}
