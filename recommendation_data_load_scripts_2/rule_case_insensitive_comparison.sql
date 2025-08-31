SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_case_insensitive_comparison
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    schema_name TEXT,
    table_name TEXT,
    log_id TEXT,
    query TEXT,
    version_id BIGINT,
    user_name TEXT;

INSERT INTO rule_case_insensitive_comparison
SELECT DISTINCT
    1 as rule_id,
    'hsbc' as org_id,
    target_database as project_name,
    target_schema as schema_name,
    target_entity_name as table_name,
    log_id,
    query,
    version_id,
    user_name
FROM base_query_info
WHERE version_id = {version} AND has_case_insensitive_comparison = TRUE;
