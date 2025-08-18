package com.calcite_new.sql.model.entity;

import com.calcite_new.sql.model.entity.context.clause.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "statement_context")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementContext {

     @Id
     @GeneratedValue(strategy = GenerationType.IDENTITY)
     private Long id;

    @OneToOne
    @JoinColumn(name = "sql_statement_info_id")
    private SqlStatementInfo sqlStatementInfo;

    @Embedded
    private SelectClause selectClause;

    @Embedded
    private WhereClause whereClause;

    @Embedded
    private FromClause fromClause;

    @Embedded
    private GroupByClause groupByClause;

    @Embedded
    private HavingClause havingClause;

    @Embedded
    private OrderByClause orderByClause;

    @Embedded
    private LimitClause limitClause;

    @Embedded
    private JoinClause joinClause;

/*    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "query_context_id")
    private List<WithClause> cteDefinitions;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "query_context_id")
    private List<JoinClause> joins;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "query_context_id")
    private List<WindowFunctionInfo> windowFunctions;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "query_context_id")
    private List<FunctionInfo> functionList;*/

    public void merge(StatementContext other) {
        if (other == null) return;

        if (other.selectClause != null) this.selectClause = other.selectClause;
        if (other.whereClause != null) this.whereClause = other.whereClause;
        if (other.groupByClause != null) this.groupByClause = other.groupByClause;
        if (other.havingClause != null) this.havingClause = other.havingClause;
        if (other.orderByClause != null) this.orderByClause = other.orderByClause;
        if (other.limitClause != null) this.limitClause = other.limitClause;
        if (other.fromClause != null) this.fromClause = other.fromClause;

/*        if (other.cteDefinitions != null) this.cteDefinitions = other.cteDefinitions;
        if (other.joins != null) this.joins = other.joins;
        if (other.windowFunctions != null) this.windowFunctions = other.windowFunctions;
        if (other.functionList != null) this.functionList = other.functionList;*/
    }
}