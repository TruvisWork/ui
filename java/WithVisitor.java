package com.calcite_new.sql.core.processor.visitor;

import com.calcite_new.core.model.EntityCatalog;
import com.calcite_new.sql.model.enums.StatementType;
import com.calcite_new.sql.relationextractor.RelationshipExtractor;
import org.apache.calcite.sql.*;

public class WithVisitor extends BaseStatementVisitor {

	public WithVisitor(String userName, String defaultDatabase, String defaultSchema, 
	                   RelationshipExtractor relationshipExtractor, EntityCatalog entityCatalog) {
		super(userName, defaultDatabase, defaultSchema, relationshipExtractor, entityCatalog);
	}

	@Override
	public SqlNodeVisitor.Result visit(SqlCall call) {
		if (!(call instanceof SqlWith withStatement)) {
			return new SqlNodeVisitor.Result();
		}

		SqlNodeVisitor.Result result = new SqlNodeVisitor.Result();
		result.setStatementType(StatementType.WITH);

		// Process CTE definitions - each CTE can reference previous ones
		// CTEs are registered as they are processed
		processCteDefinitions(withStatement, result);

		// Process main body directly with all CTE aliases available
		processMainQuery(withStatement, result);

		return result;
	}

	private void processCteDefinitions(SqlWith withStatement, SqlNodeVisitor.Result result) {
		if (withStatement.withList == null) {
			return;
		}

		// Process each CTE in order, allowing later CTEs to reference earlier ones
		for (SqlNode withItem : withStatement.withList) {
			if (withItem instanceof SqlWithItem cteItem) {
				String cteName = cteItem.name != null ? cteItem.name.getSimple() : null;
				SqlNode cteQuery = cteItem.query;
				
				if (cteQuery != null) {
					// For nested CTEs, pass already-registered CTE aliases (from previous CTEs)
					if (cteQuery instanceof SqlSelect selectQuery) {
						SelectVisitor selectVisitor = new SelectVisitor(userName, defaultDatabase, defaultSchema, 
						                                                 relationshipExtractor, entityCatalog);
						
						// Pass previously registered CTE aliases so this CTE can reference earlier ones
						selectVisitor.setCteAliases(result.getContext().getCteAliases());
						
						SqlNodeVisitor.Result cteResult = selectVisitor.visit(selectQuery);
						
						// Merge CTE results into main result (relationships and source tables)
						SqlNodeVisitor.mergeResults(result, cteResult);
					} else {
						// For non-SELECT CTEs, use general visitor
						SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, 
						                                             relationshipExtractor, entityCatalog);
						SqlNodeVisitor.Result cteResult = cteQuery.accept(visitor);

						// Copy CTE aliases to the result
						for (String cteAlias : result.getContext().getCteAliases()) {
							cteResult.getContext().addCteAlias(cteAlias);
						}

						// Merge CTE results into main result (relationships and source tables)
						SqlNodeVisitor.mergeResults(result, cteResult);
					}
				}
				
				// Register this CTE AFTER processing its query
				// This ensures the CTE can't reference itself and is available for subsequent CTEs
				if (cteName != null) {
					result.getContext().addCteAlias(cteName);
				}
			}
		}
	}

	private void processMainQuery(SqlWith withStatement, SqlNodeVisitor.Result result) {
		if (withStatement.body == null) {
			return;
		}

		// Process the main body based on its type, passing CTE context
		if (withStatement.body instanceof SqlSelect selectBody) {
			// Call SelectVisitor directly and pass CTE aliases
			SelectVisitor selectVisitor = new SelectVisitor(userName, defaultDatabase, defaultSchema, 
			                                                 relationshipExtractor, entityCatalog);
			
			// CRITICAL: Pass CTE aliases BEFORE processing
			selectVisitor.setCteAliases(result.getContext().getCteAliases());
			
			SqlNodeVisitor.Result bodyResult = selectVisitor.visit(selectBody);
			
			// Merge body results into main result
			SqlNodeVisitor.mergeResults(result, bodyResult);
			
			// Add access for the tables
			addAccess(withStatement.body, result);
		} else if (withStatement.body instanceof SqlInsert insertBody) {
			// Call InsertVisitor directly for INSERT statements
			InsertVisitor insertVisitor = new InsertVisitor(userName, defaultDatabase, defaultSchema, 
			                                                 relationshipExtractor, entityCatalog);
			
			// CRITICAL: Pass CTE aliases BEFORE processing
			insertVisitor.setCteAliases(result.getContext().getCteAliases());
			
			SqlNodeVisitor.Result bodyResult = insertVisitor.visit(insertBody);
			
			// Merge body results into main result
			SqlNodeVisitor.mergeResults(result, bodyResult);
		} else {
			// For other statement types, use the general visitor
			SqlNodeVisitor visitor = new SqlNodeVisitor(userName, defaultDatabase, defaultSchema, 
			                                             relationshipExtractor, entityCatalog);
			SqlNodeVisitor.Result bodyResult = withStatement.body.accept(visitor);
			
			// Copy CTE aliases to body result
			for (String cteAlias : result.getContext().getCteAliases()) {
				bodyResult.getContext().addCteAlias(cteAlias);
			}
			
			// Merge body results into main result
			SqlNodeVisitor.mergeResults(result, bodyResult);
		}
	}
}

