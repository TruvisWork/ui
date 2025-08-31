SET search_path to {schema};

-- Create the table
CREATE TABLE IF NOT EXISTS {schema}.partition_metadata (
  source_product TEXT,
  instance TEXT,
  "database" TEXT,
  version_id BIGINT,
  schema TEXT,
  table_name TEXT,
  partition_name TEXT,
  partition_columns TEXT[],  
  partition_text TEXT,
  user_name TEXT,
  partition_size_bytes BIGINT,
  create_at BIGINT
);

-- Insert data into the table
INSERT INTO {schema}.partition_metadata (
  source_product,
  instance,
  "database",
  version_id,
  schema,
  table_name,
  partition_name,
  partition_columns,
  partition_text,
  user_name,
  partition_size_bytes,
  create_at
)
SELECT
  'BIG_QUERY' AS source_product,
  '{instance}' AS instance,
  col."TABLE_CATALOG" AS "database",
  {version} AS version_id,
  col."TABLE_SCHEMA" AS schema,
  col."TABLE_NAME" AS table_name,
  NULL AS partition_name,
  ARRAY_AGG(col."COLUMN_NAME" ORDER BY col."COLUMN_NAME") AS partition_columns,
  NULL AS partition_text,
  NULL AS user_name,
  0 AS partition_size_bytes,
  EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at

FROM
  {schema}."COLUMNS" col
WHERE
  "IS_PARTITIONING_COLUMN" = 'YES'
GROUP BY
  col."TABLE_CATALOG",
  col."TABLE_SCHEMA",
  col."TABLE_NAME";
