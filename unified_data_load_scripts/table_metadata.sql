SET search_path to {schema};

-- Create the table with metadata
CREATE TABLE IF NOT EXISTS {schema}.table_metadata (
    source_product TEXT,
    version_id BIGINT,
    "database" TEXT,
    "schema" TEXT,
    table_name TEXT,
    username TEXT,
    size_mb DOUBLE PRECISION,
    create_at BIGINT,
    update_at BIGINT
);

-- Insert or update metadata
INSERT INTO {schema}.table_metadata
SELECT
    'BIG_QUERY' AS source_product,
    {version} AS version_id,
    t."TABLE_CATALOG" AS "database",
    t."TABLE_SCHEMA" AS "schema",
    t."TABLE_NAME" AS table_name,
    '' AS username,
    COALESCE(ts.size_bytes / (1024.0 * 1024.0), 0.0) AS size_mb,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at,
    COALESCE(EXTRACT(EPOCH FROM CURRENT_TIMESTAMP),  EXTRACT(EPOCH FROM t."CREATION_TIME"))::BIGINT AS update_at
FROM
    {schema}."TABLES" t
LEFT JOIN
    {schema}."TABLES__" ts
    ON t."TABLE_CATALOG" = ts.project_id
       AND t."TABLE_SCHEMA" = ts.dataset_id
       AND t."TABLE_NAME" = ts.table_id
WHERE
    t."TABLE_TYPE" = 'BASE TABLE';
