SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_join_on_string_column
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    join_condition TEXT,
    log_id TEXT,
    version_id BIGINT,
    user_name TEXT,
    query TEXT;

INSERT INTO rule_join_on_string_column
SELECT DISTINCT
    12 as rule_id,
    'hsbc' as org_id,
    target_database as project_name,
    join_condition,
    log_id,
    version_id,
    user_name,
    query
FROM base_query_info
WHERE version_id = {version} AND has_join_on_string_column = TRUE;