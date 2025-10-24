SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_function_on_partition (
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    schema_name TEXT,
    table_name TEXT,
    log_id TEXT,
    query TEXT,
    version_id BIGINT,
    user_name TEXT
);

INSERT INTO rule_function_on_partition
SELECT DISTINCT
    17 as rule_id,
    'hsbc' as org_id,
    database as project_name,
    log_id,
    query,
    version_id,
    user_name
FROM base_query_info
WHERE version_id = {version} AND
    has_function_on_partition_column = TRUE;