SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_delete_with_true
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    schema_name TEXT,
    table_name TEXT,
    log_id TEXT,
    query TEXT,
    version_id BIGINT,
    user_name TEXT,
    table_size DOUBLE PRECISION,
    cost DOUBLE PRECISION;

INSERT INTO rule_delete_with_true
SELECT DISTINCT
    2 as rule_id,
    'hsbc' as org_id,
    target_database as project_name,
    target_schema as schema_name,
    target_entity_name as table_name,
    log_id,
    query,
    version_id,
    user_name,
    table_size,
    0.0 as cost
FROM base_query_info
WHERE version_id = {version} AND
    (statement_type = 'DELETE'
    AND has_true_condition = TRUE
    AND target_schema IS NOT NULL
    AND relationship_type = 'ACCESSES');