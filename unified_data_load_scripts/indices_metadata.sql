SET search_path to {schema};

-- Create the table
CREATE TABLE IF NOT EXISTS {schema}.indices_metadata (
  source_product TEXT,
  "database" TEXT,
  version_id BIGINT,
  schema TEXT,
  index_name TEXT,
  user_name TEXT,
  column_list TEXT[], 
  index_identifier TEXT,
  index_type TEXT,
  ddl_statement TEXT,
  is_unique BOOLEAN,
  create_at BIGINT,
  update_at BIGINT,
  instance TEXT
);

-- Insert data into the table
INSERT INTO {schema}.indices_metadata (
  source_product,
  instance,
  "database",
  version_id,
  schema,
  index_name,
  column_list,
  index_type,
  index_identifier,
  user_name,
  ddl_statement,
  is_unique,
  create_at,
  update_at
)
SELECT
  source_product,
  instance,
  table_catalog,
  {version} AS version_id,
  table_schema,
  table_name,
  ARRAY_AGG(column_name) AS column_list,
  'cluster' AS index_type,
  NULL AS index_identifier,
  NULL AS user_name,
  NULL AS ddl_statement,
  FALSE AS is_unique,
  EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at,
  NULL AS update_at
FROM (
  SELECT
    'BIG_QUERY' AS source_product,
    '{instance}' AS instance,   
    c."TABLE_CATALOG" AS table_catalog,
    c."TABLE_SCHEMA" AS table_schema,
    c."TABLE_NAME" AS table_name,
    c."CLUSTERING_ORDINAL_POSITION" AS clustering_ordinal_position,
    c."COLUMN_NAME" AS column_name
  FROM
    {schema}."COLUMNS" c
  WHERE
    c."CLUSTERING_ORDINAL_POSITION" IS NOT NULL
    ORDER BY c."CLUSTERING_ORDINAL_POSITION"
) AS clustering_columns
GROUP BY
  source_product,
  instance,
  table_catalog,
  table_schema,
  table_name;