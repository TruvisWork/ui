SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_unused_window_function (
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    log_id TEXT,
    query TEXT,
    version_id BIGINT,
    user_name TEXT
);

INSERT INTO rule_unused_window_function
SELECT DISTINCT
    18 as rule_id,
    'hsbc' as org_id,
    database as project_name,
    log_id,
    query,
    version_id,
    user_name
FROM base_query_info
WHERE version_id = {version} AND
    has_unused_window_function = TRUE;