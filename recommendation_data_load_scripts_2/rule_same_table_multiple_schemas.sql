SET search_path to {schema};
DROP TABLE IF EXISTS rule_same_table_multiple_schemas_flat CASCADE;

CREATE TABLE rule_same_table_multiple_schemas_flat AS
WITH query_details AS (
  SELECT "TABLE_CATALOG" as table_catalog, "TABLE_SCHEMA" as table_schema, "TABLE_NAME" as table_name, "DDL" AS query
  FROM "TABLES"
  WHERE "TABLE_TYPE" = 'BASE TABLE'
),
flattened_columns AS (
  SELECT
    database AS project_name,
    version_id,
    schema AS schema_name,
    "table" AS table_name,
    CONCAT(
      column_name, ':', data_type
    ) AS column_type_pair
  FROM column_metadata
  WHERE version_id = {version}
),
table_structures AS (
  SELECT
    project_name,
    version_id,
    schema_name,
    table_name,
    STRING_AGG(column_type_pair, ',' ORDER BY column_type_pair) AS columns_with_data_type
  FROM flattened_columns
  GROUP BY project_name, version_id, schema_name, table_name
),
ranked_data AS (
  SELECT
    10 AS rule_id,
    'hsbc' AS org_id,
    ts.version_id,
    ts.project_name,
    ts.schema_name,
    ts.table_name,
    ts.columns_with_data_type,
    COUNT(*) OVER (PARTITION BY ts.columns_with_data_type) AS partitioned_row_count,
    qd.query
  FROM table_structures ts
  LEFT JOIN query_details qd
    ON ts.project_name = qd.table_catalog
    AND ts.schema_name = qd.table_schema
    AND ts.table_name = qd.table_name
)
SELECT
  rd.rule_id,
  rd.org_id,
  rd.version_id,
  rd.project_name,
  rd.schema_name,
  rd.table_name,
  te.size_mb AS table_size,
  rd.columns_with_data_type,
  rd.query
FROM ranked_data rd
LEFT JOIN table_metadata te
  ON rd.project_name = te.database
  AND rd.schema_name = te.schema
  AND rd.table_name = te.table_name
WHERE te.version_id = {version} AND rd.partitioned_row_count > 1;

-- Step 2: Aggregate similar tables and calculate storage savings

SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_same_table_multiple_schemas
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    project_name TEXT,
    log_id TEXT,
    schema_name TEXT,
    query TEXT,
    table_name TEXT,
    table_size DOUBLE PRECISION,
    columns_with_data_type TEXT,
    cost DOUBLE PRECISION;

INSERT INTO rule_same_table_multiple_schemas
WITH shared_structures AS (
    SELECT
        f1.project_name,
        f1.schema_name,
        f1.table_name,
        f1.table_size,
        f1.columns_with_data_type,
        'NA' as log_id,
        COALESCE(f1.query, 'NA') as query,
        10 AS rule_id,
        'hsbc' AS org_id,
        f1.version_id,
        ROUND(
            (f1.table_size / 1024.0) *
            CAST(COALESCE(0.06, 0.02) AS DECIMAL),
            3
        ) AS cost
    FROM rule_same_table_multiple_schemas_flat f1
    INNER JOIN (
        SELECT columns_with_data_type
        FROM rule_same_table_multiple_schemas_flat
        GROUP BY columns_with_data_type
        HAVING COUNT(*) > 1
    ) duplicates
    ON f1.columns_with_data_type = duplicates.columns_with_data_type
)
SELECT *
FROM shared_structures
ORDER BY table_size DESC;