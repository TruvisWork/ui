-- Drop the table if it exists
SET search_path to {schema};

-- Create the function_metadata table
CREATE TABLE IF NOT EXISTS {schema}.function_metadata (
  source_product TEXT,
  "database" TEXT,
  version_id BIGINT,    
  schema TEXT,
  function_name TEXT,
  sql_query TEXT,
  user_name TEXT,
  create_at BIGINT,
  update_at BIGINT,
  instance TEXT
);

-- Insert data into the function_metadata table
INSERT INTO {schema}.function_metadata
SELECT
  'BIG_QUERY' AS source_product,
  r."ROUTINE_CATALOG" AS "database",
  {version} AS version_id,
  r."ROUTINE_SCHEMA" AS schema,
  r."ROUTINE_NAME" AS function_name,
  r."DDL" AS sql_query,   
  '' AS user_name,
  CAST(EXTRACT(EPOCH FROM r."CREATED") AS BIGINT) AS create_at,
  CAST(EXTRACT(EPOCH FROM r."LAST_ALTERED") AS BIGINT) AS update_at,
  '{instance}' AS instance
FROM
  {schema}."ROUTINES" r
WHERE
  r."ROUTINE_TYPE" = 'FUNCTION';
