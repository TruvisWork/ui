-- Drop temporary table if it exists
SET search_path to {schema};
DROP TABLE IF EXISTS {schema}.column_metadata_temp CASCADE;

-- Create temporary table
CREATE TABLE {schema}.column_metadata_temp (
    source_product TEXT,
    "database" TEXT,
    schema TEXT,
    "table" TEXT,
    column_name TEXT,
    username TEXT,
    column_position BIGINT,
    data_type TEXT,
    column_length BIGINT,
    column_precision BIGINT,
    column_scale BIGINT,
    column_format TEXT,
    default_value TEXT,
    nullable BOOLEAN,
    is_uppercase BOOLEAN,
    is_case_sensitive BOOLEAN,
    column_constraint TEXT,
    compressible BOOLEAN,
    compress_value_list TEXT,
    create_at BIGINT,
    update_at BIGINT,
    instance TEXT
);

-- Insert into temp table with regex logic
INSERT INTO {schema}.column_metadata_temp
SELECT
    'BIG_QUERY' AS source_product,
    col."TABLE_CATALOG" AS "database",
    col."TABLE_SCHEMA" AS schema,
    col."TABLE_NAME" AS table,
    col."COLUMN_NAME" AS column_name,
    '' AS username,
    RANK() OVER (
        PARTITION BY col."TABLE_CATALOG", col."TABLE_SCHEMA", col."TABLE_NAME"
        ORDER BY CAST(col."ORDINAL_POSITION" AS BIGINT)
    ) AS column_position,

    CASE
        WHEN UPPER(data_type_name) = 'BOOL' THEN 'BOOLEAN'
        WHEN UPPER(data_type_name) = 'BYTES' THEN 'VARBINARY'
        WHEN UPPER(data_type_name) = 'DATE' THEN 'DATE'
        WHEN UPPER(data_type_name) = 'DATETIME' THEN 'TIMESTAMP'
        WHEN UPPER(data_type_name) = 'GEOGRAPHY' THEN 'VARCHAR'
        WHEN UPPER(data_type_name) = 'INTERVAL' THEN 'INTERVAL_YEAR_MONTH'
        WHEN UPPER(data_type_name) = 'JSON' THEN 'JSON'
        WHEN UPPER(data_type_name) = 'INT64' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'INT' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'SMALLINT' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'INTEGER' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'BIGINT' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'TINYINT' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'BYTEINT' THEN 'BIGINT'
        WHEN UPPER(data_type_name) = 'NUMERIC' THEN 'DECIMAL'
        WHEN UPPER(data_type_name) LIKE 'DECIMAL' THEN 'DECIMAL'
        WHEN UPPER(data_type_name) = 'BIGNUMERIC' THEN 'DECIMAL'
        WHEN UPPER(data_type_name) = 'BIGDECIMAL' THEN 'DECIMAL'
        WHEN UPPER(data_type_name) = 'TIME' THEN 'TIME'
        WHEN UPPER(data_type_name) = 'TIMESTAMP' THEN 'TIMESTAMP'
        WHEN UPPER(data_type_name) = 'FLOAT64' THEN 'DOUBLE'
        WHEN UPPER(data_type_name) = 'STRING' THEN 'VARCHAR'
        WHEN UPPER(data_type_name) LIKE 'ARRAY<STRUCT%' THEN 'ARRAY'
        WHEN UPPER(data_type_name) LIKE 'STRUCT%' THEN 'STRUCT'
        WHEN UPPER(data_type_name) LIKE 'ARRAY%' THEN 'ARRAY'
        ELSE 'ANY'
    END AS data_type,

    COALESCE(CAST(data_type_precision AS BIGINT), 0),
    COALESCE(CAST(data_type_precision AS BIGINT), 0),
    COALESCE(CAST(data_type_scale AS BIGINT), 0),

    CAST(NULL AS TEXT) AS column_format,
    CAST(NULL AS TEXT) AS default_value,
    FALSE AS nullable,
    FALSE AS is_uppercase,
    FALSE AS is_case_sensitive,
    CAST(NULL AS TEXT) AS column_constraint,
    FALSE AS compressible,
    CAST(NULL AS TEXT) AS compress_value_list,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at,
    NULL AS update_at,
    '{instance}' AS instance

FROM
(
    SELECT 
        col.*,
        TRIM(SUBSTRING(col."DATA_TYPE" FROM '^[^\\(\\s]+')) AS data_type_name,
        TRIM(SUBSTRING(col."DATA_TYPE" FROM '\\((\\d+)\\)')) AS data_type_precision,
        TRIM(SUBSTRING(col."DATA_TYPE" FROM '\\((\\d+),(\\d+)\\)')) AS data_type_scale
    FROM {schema}."COLUMNS" col
) col
LEFT JOIN {schema}."TABLES" t
ON col."TABLE_CATALOG" = t."TABLE_CATALOG"
AND col."TABLE_SCHEMA" = t."TABLE_SCHEMA"
AND col."TABLE_NAME" = t."TABLE_NAME";

-- Create final table
CREATE TABLE IF NOT EXISTS {schema}.column_metadata (
    source_product TEXT,
    "database" TEXT,
    version_id BIGINT,
    schema TEXT,
    "table" TEXT,
    column_name TEXT,
    username TEXT,
    column_position BIGINT,
    data_type TEXT,
    column_length BIGINT,
    column_precision BIGINT,
    column_scale BIGINT,
    column_format TEXT,
    default_value TEXT,
    nullable BOOLEAN,
    is_uppercase BOOLEAN,
    is_case_sensitive BOOLEAN,
    column_constraint TEXT,
    compressible BOOLEAN,
    compress_value_list TEXT,
    create_at BIGINT,
    update_at BIGINT,
    instance TEXT
);

-- Insert from temp to final table
INSERT INTO {schema}.column_metadata
SELECT
    source_product,
    "database",
    {version} AS version_id,
    schema,
    "table",
    column_name,
    username,
    column_position,
    data_type,
    column_length,
    column_precision,
    column_scale,
    column_format,
    default_value,
    nullable,
    is_uppercase,
    is_case_sensitive,
    column_constraint,
    compressible,
    compress_value_list,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS create_at,
    EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT AS update_at,
    instance
FROM {schema}.column_metadata_temp
WHERE schema IS NOT NULL
  AND "table" IS NOT NULL
  AND column_name IS NOT NULL;

COMMIT;
