SET search_path to {schema};

CREATE TABLE IF NOT EXISTS recommendation_table (
    rule_id INT,
    rule_title TEXT,
    project_id TEXT,
    no_of_queries BIGINT,
    total_queries BIGINT,
    sample_query TEXT,
    no_of_schemas BIGINT,
    recommendation TEXT,
    version_id BIGINT
);

WITH global_total AS (
    SELECT COUNT(*) AS total_queries
    FROM sql_statement_info
    WHERE version_id = {version}
),
combined_rules AS (
    SELECT rule_id, project_name AS project_id, schema_name, query
    FROM rule_delete_with_true
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_jobs_failing_very_frequently_due_to_resource_error
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_jobs_failing_very_frequently
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_order_by_inside_subquery
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_multiple_updates_on_a_single_table
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_in_clause_has_subquery
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_in_clause_has_constants
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_case_insensitive_comparison
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_table_clone
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_same_table_multiple_schemas
    WHERE version_id = {version}
    UNION ALL
    SELECT rule_id, project_name, schema_name, query
    FROM rule_jobs_scanning_high_volume_of_data
    WHERE version_id = {version}
),
rule_stats AS (
    SELECT
        rule_id,
        project_id,
        COUNT(*) AS no_of_queries,
        COUNT(DISTINCT schema_name) AS no_of_schemas,
        MIN(query) AS sample_query
    FROM combined_rules
    GROUP BY rule_id, project_id
)
INSERT INTO recommendation_table
SELECT
    rs.rule_id,
    rm.rule_title,
    rs.project_id,
    rs.no_of_queries,
    gt.total_queries,
    rs.sample_query,
    rs.no_of_schemas,
    rm.recommendation,
    {version} AS version_id
FROM rule_stats rs
CROSS JOIN global_total gt
JOIN rule_master rm ON rs.rule_id = rm.rule_id
ORDER BY rs.rule_id, rs.project_id;
