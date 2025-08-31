SET search_path to {schema};

-- Create the table
CREATE TABLE IF NOT EXISTS {schema}.view_metadata (
  source_product TEXT,
  "database" TEXT,
  version_id BIGINT,
  schema TEXT,
  view_name TEXT,
  sql_query TEXT,
  view_type TEXT,
  username TEXT,
  create_at BIGINT,
  update_at BIGINT,
  instance TEXT
);

-- Insert the data
INSERT INTO {schema}.view_metadata
SELECT
  'BIG_QUERY' AS source_product,
  t."TABLE_CATALOG" AS "database",
  {version} AS version_id,
  t."TABLE_SCHEMA" AS schema,
  t."TABLE_NAME" AS view_name,
  t."DDL" AS sql_query,
  t."TABLE_TYPE" AS view_type,
  '' AS username,
  CASE
    WHEN CURRENT_TIMESTAMP IS NOT NULL
    THEN EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT
    ELSE NULL
  END AS create_at,
  CASE
    WHEN CURRENT_TIMESTAMP IS NOT NULL
    THEN EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT
    ELSE EXTRACT(EPOCH FROM to_timestamp(ts.last_modified_time))::BIGINT
  END AS update_at,
  '{instance}' AS instance
FROM
  {schema}."TABLES" t
LEFT JOIN
  {schema}."TABLES__" ts 
  ON 
  t."TABLE_CATALOG" = ts.project_id
  AND t."TABLE_SCHEMA" = ts.dataset_id
  AND t."TABLE_NAME" = ts.table_id
WHERE
  t."TABLE_TYPE" IN ('MATERIALIZED VIEW', 'VIEW');
