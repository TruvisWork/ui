package com.calcite_new.sql.model.entity.context.clause;

import com.calcite_new.sql.model.entity.ColumnInfo;
import jakarta.persistence.*;
import lombok.*;
import org.apache.calcite.sql.JoinType;
import java.util.List;
import com.calcite_new.sql.core.processor.utils.JoinAnalyzer;

@Entity
@Table(name = "join_clause")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JoinClause {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String leftTable;
    private String rightTable;
    @Enumerated(EnumType.STRING)
    private JoinType joinType;
    private Boolean isEquiJoin;
    private String joinCondition;
    private Boolean hasJoinOnStringColumn;
    @ElementCollection
    @CollectionTable(name = "join_clause_columns", joinColumns = @JoinColumn(name = "join_clause_id"))
    private List<ColumnInfo> joinColumns;
}
