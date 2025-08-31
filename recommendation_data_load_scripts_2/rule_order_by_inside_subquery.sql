SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_order_by_inside_subquery
    rule_id BIGINT,
    org_id TEXT,
    project_name TEXT,
    schema_name TEXT,
    log_id TEXT,
    query TEXT,
    version_id BIGINT,
    user_name TEXT,
    cost DOUBLE PRECISION;

INSERT INTO rule_order_by_inside_subquery
SELECT DISTINCT
    9 as rule_id,
    'hsbc' as org_id,
    database as project_name,
    schema as schema_name,
    log_id,
    query,
    version_id,
    user_name,
    0.0 as cost
FROM base_query_info
WHERE version_id = {version} AND is_oder_by_inside_sub_query = TRUE;