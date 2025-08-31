-- Drop the table if it exists
SET search_path to {schema};

-- Create the external table
CREATE TABLE IF NOT EXISTS {schema}.external_table_metadata
    source_product TEXT,
    "database" TEXT,
    schema TEXT,
    external_table_name TEXT,
    external_table_type TEXT,
    external_object_name TEXT,
    create_at BIGINT,
    update_at BIGINT,
    instance TEXT,
    version_id BIGINT;

INSERT INTO {schema}.external_table_metadata
SELECT
    'BIG_QUERY' AS source_product, 
    t."TABLE_CATALOG" AS "database",  
    t."TABLE_SCHEMA" AS "schema",
    t."TABLE_NAME" AS external_table_name,
    t."TABLE_TYPE" AS external_table_type,
    REGEXP_REPLACE(
        (REGEXP_MATCHES(t."DDL", 'uris\s*=\s*\[(.*?)\]', 'i'))[1], '"', '', 'g'
    ) AS external_object_name,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS update_at,
    '{instance}' AS instance,
    {version} AS version_id
FROM
    {schema}."TABLES" t  
WHERE
    t."TABLE_TYPE" = 'EXTERNAL';  
