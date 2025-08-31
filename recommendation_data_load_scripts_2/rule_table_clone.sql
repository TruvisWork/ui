SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_table_clone
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    project_name TEXT,
    log_id TEXT,
    schema_name TEXT,
    target_database TEXT,
    target_entity_name TEXT,
    source_database TEXT,
    query TEXT,
    user_name TEXT,
    cost DOUBLE PRECISION;

WITH clone_candidates AS (
    SELECT *,
        COUNT(*) FILTER (WHERE relationship_type = 'DEPENDS_ON')
        OVER (PARTITION BY s_id) AS depends_on_count
    FROM base_query_info
    WHERE version_id = {version}
)
INSERT INTO rule_table_clone
SELECT DISTINCT
    11 AS rule_id,
    'hsbc' AS org_id,
    database as project_name,
    target_database,
    target_schema as schema_name,
    target_entity_name,
    source_database,
    log_id,
    query,
    version_id,
    user_name,
    0.0 AS cost
FROM clone_candidates
WHERE statement_type = 'INSERT'
    AND has_select_all = TRUE
    AND has_where_clause <> TRUE
    AND depends_on_count = 1;
