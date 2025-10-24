package com.calcite_new.sql.model.entity;

import com.calcite_new.sql.model.enums.ClauseType;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing column usage information in SQL statements.
 * Tracks which columns are used in different SQL clauses (WHERE, GROUP BY, JOIN, etc.)
 */
@Entity
@Table(name = "column_info")
@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter
@Builder
@EqualsAndHashCode(of = {"product", "database", "schema", "tableName", "columnName", "clause"})
public class ColumnInfo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String product;
	private String database;
	private String schema;
	
	@Column(name = "table_name")
	private String tableName;
	
	@Column(name = "column_name")
	private String columnName;

	@Enumerated(EnumType.STRING)
	private ClauseType clause;

	@ManyToOne
	@JoinColumn(name = "sql_statement_info_id")
	private SqlStatementInfo sqlStatementInfo;
}

