package com.calcite_new.sql.core.processor.utils;

import com.calcite_new.sql.SqlColumnIdentifier;
import com.calcite_new.sql.SqlTableIdentifier;
import com.calcite_new.sql.SqlViewIdentifier;
import com.calcite_new.sql.core.processor.visitor.SqlComputedColumnIdentifier;
import com.calcite_new.sql.core.processor.visitor.SqlOrdinalReference;
import com.calcite_new.sql.model.entity.ColumnInfo;
import com.calcite_new.sql.model.enums.ClauseType;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class to extract column information from SQL expressions.
 * Used to track which columns are used in different SQL clauses.
 * Handles computed columns and GROUP BY ordinals.
 */
@Slf4j
public class ColumnExtractor {

	/**
	 * Extracts all columns from a SQL node (expression, condition, etc.)
	 * For GROUP BY, pass the SELECT list to resolve ordinals
	 */
	public static List<ColumnInfo> extractColumns(SqlNode node, ClauseType clauseType, String product) {
		List<ColumnInfo> columnInfos = new ArrayList<>();
		if (node == null) {
			return columnInfos;
		}
		extractColumnsRecursive(node, clauseType, product, columnInfos);
		return columnInfos;
	}

	/**
	 * Extracts columns from GROUP BY clause.
	 * SqlOrdinalReference and SqlComputedColumnIdentifier are already handled by the enricher,
	 * so we just need to recursively extract from the enriched nodes.
	 */
	public static List<ColumnInfo> extractGroupByColumns(SqlNodeList groupByList, SqlNodeList selectList, String product) {
		List<ColumnInfo> columnInfos = new ArrayList<>();
		if (groupByList == null) {
			return columnInfos;
		}

		// Simply extract recursively - SqlOrdinalReference will be handled automatically
		for (SqlNode groupByItem : groupByList.getList()) {
			extractColumnsRecursive(groupByItem, ClauseType.GROUP_BY, product, columnInfos);
		}

		return columnInfos;
	}


	private static void extractColumnsRecursive(SqlNode node, ClauseType clauseType, String product, List<ColumnInfo> columnInfos) {
		if (node == null) {
			return;
		}

		// Handle SqlOrdinalReference - extract from the referenced SELECT item
		if (node instanceof SqlOrdinalReference ordinalRef) {
			log.debug("Found ordinal reference {} in {}, resolving to: {}", 
			          ordinalRef.getOrdinalPosition(), clauseType, ordinalRef.getReferencedSelectItem());
			// Extract columns from the actual SELECT item that this ordinal references
			extractColumnsRecursive(ordinalRef.getReferencedSelectItem(), clauseType, product, columnInfos);
			return;
		}

		// Handle SqlComputedColumnIdentifier - extract from the source expression
		if (node instanceof SqlComputedColumnIdentifier computedCol) {
			log.debug("Found computed column {} in {}, extracting from source expression", 
			          computedCol.getAliasName(), clauseType);
			// If it's a simple column alias, extract it normally
			// If it's a computed expression, extract underlying columns from the source expression
			extractColumnsRecursive(computedCol.getSourceExpression(), clauseType, product, columnInfos);
			return;
		}

		// Handle regular column identifiers
		if (node instanceof SqlColumnIdentifier columnId) {
			ColumnInfo columnInfo = createColumnInfo(columnId, clauseType, product);
			if (columnInfo != null && !columnInfos.contains(columnInfo)) {
				columnInfos.add(columnInfo);
			}
		}
		// Handle identifier that might be a column
		else if (node instanceof SqlIdentifier id && !(id instanceof SqlTableIdentifier) && !(id instanceof SqlViewIdentifier)) {
			// This is likely a column reference
			ColumnInfo columnInfo = createColumnInfoFromIdentifier(id, clauseType, product);
			if (columnInfo != null && !columnInfos.contains(columnInfo)) {
				columnInfos.add(columnInfo);
			}
		}
		// Recursively process call operands
		else if (node instanceof SqlCall call) {
			for (SqlNode operand : call.getOperandList()) {
				extractColumnsRecursive(operand, clauseType, product, columnInfos);
			}
		}
		// Handle node lists
		else if (node instanceof SqlNodeList nodeList) {
			for (SqlNode item : nodeList.getList()) {
				extractColumnsRecursive(item, clauseType, product, columnInfos);
			}
		}
	}

	private static ColumnInfo createColumnInfo(SqlColumnIdentifier columnId, ClauseType clauseType, String product) {
		try {
			// CRITICAL: Use DatabaseEntity for table info - this has the actual source table, not CTE aliases
			// The enricher should have already set this correctly
			String columnName = columnId.getColumnName();
			String database = null;
			String schema = null;
			String tableName = null;
			String extractedProduct = product; // Default to passed product

			// Get from DatabaseEntity (this has the actual table, not CTE)
			if (columnId.getDatabaseEntity() != null) {
				var entity = columnId.getDatabaseEntity();
				var namespace = entity.getNamespace();
				
				if (namespace != null && namespace.size() >= 2) {
					// Namespace structure: [product, database, schema] - extract all
					if (namespace.size() >= 3) {
						extractedProduct = namespace.get(0).getNormalizedName(); // product
						database = namespace.get(1).getNormalizedName();       // database
						schema = namespace.get(2).getNormalizedName();         // schema
					} else if (namespace.size() == 2) {
						database = namespace.get(0).getNormalizedName();       // database
						schema = namespace.get(1).getNormalizedName();         // schema
					}
				}
				tableName = entity.getName().getNormalizedName();
				
				// If we don't have column name yet, get from Column entity
				if (columnName == null && columnId.getEntity() != null) {
					columnName = columnId.getEntity().getName().getNormalizedName();
				}
			}
			// If DatabaseEntity is null, use Column entity namespace
			else if (columnId.getEntity() != null) {
				com.calcite_new.core.model.entity.Column columnEntity = columnId.getEntity();
				columnName = columnEntity.getName().getNormalizedName();

				// Column namespace contains: [product, database, schema, table] - extract all
				var namespace = columnEntity.getNamespace();
				if (namespace != null && !namespace.isEmpty()) {
					int size = namespace.size();
					if (size >= 4) {
						// Full namespace: product.database.schema.table
						extractedProduct = namespace.get(0).getNormalizedName(); // product
						database = namespace.get(1).getNormalizedName();        // database
						schema = namespace.get(2).getNormalizedName();           // schema
						tableName = namespace.get(3).getNormalizedName();       // table
					} else if (size == 3) {
						// database.schema.table
						database = namespace.get(0).getNormalizedName();       // database
						schema = namespace.get(1).getNormalizedName();         // schema
						tableName = namespace.get(2).getNormalizedName();      // table
					} else if (size == 2) {
						// schema.table
						schema = namespace.get(0).getNormalizedName();
						tableName = namespace.get(1).getNormalizedName();
					} else if (size == 1) {
						// table only
						tableName = namespace.get(0).getNormalizedName();
					}
				}
			}

			// Build ColumnInfo only if we have at least column name
			if (columnName == null) {
				return null;
			}

			return ColumnInfo.builder()
					.product(extractedProduct)
					.database(database)
					.schema(schema)
					.tableName(tableName)
					.columnName(columnName)
					.clause(clauseType)
					.build();
		} catch (Exception e) {
			log.debug("Failed to create ColumnInfo from SqlColumnIdentifier: {}", e.getMessage());
			return null;
		}
	}

	private static ColumnInfo createColumnInfoFromIdentifier(SqlIdentifier id, ClauseType clauseType, String product) {
		try {
			if (id.names == null || id.names.isEmpty()) {
				return null;
			}

			String columnName = id.names.get(id.names.size() - 1);
			String database = null;
			String schema = null;
			String tableName = null;

			if (id.names.size() == 4) {
				database = id.names.get(0);
				schema = id.names.get(1);
				tableName = id.names.get(2);
			} else if (id.names.size() == 3) {
				database = id.names.get(0);
				schema = id.names.get(1);
				tableName = id.names.get(2);
			} else if (id.names.size() == 2) {
				tableName = id.names.get(0);
			}

			return ColumnInfo.builder()
					.product(product)
					.database(database)
					.schema(schema)
					.tableName(tableName)
					.columnName(columnName)
					.clause(clauseType)
					.build();
		} catch (Exception e) {
			log.debug("Failed to create ColumnInfo from SqlIdentifier: {}", e.getMessage());
			return null;
		}
	}
}

