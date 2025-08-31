SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_jobs_failing_very_frequently_due_to_resource_error
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    project_name TEXT,
    log_id TEXT,
    schema_name TEXT,
    query TEXT,
    user_name TEXT,
    error_result_reason TEXT,
    error_result_message TEXT,
    total_slot_ms DOUBLE PRECISION,
    cost DOUBLE PRECISION;

INSERT INTO rule_jobs_failing_very_frequently_due_to_resource_error
SELECT
    6 as rule_id,
    'hsbc' AS org_id,
    {version} AS version_id,
    project_id AS project_name,
    'NA' AS schema_name,
    job_id as log_id,
    query,
    user_email AS user_name,
    error_result_reason,
    error_result_message,
    total_slot_ms,
    CASE
        WHEN total_slot_ms IS NULL THEN CAST(0.0 AS DOUBLE PRECISION)
        ELSE CAST(ROUND(CEIL(total_slot_ms / 1000.0 / 60.0) * COALESCE(0.06, 0.001), 3) AS DOUBLE PRECISION)
    END AS cost
FROM "JOBS_BY_PROJECT"
WHERE
    query IS NOT NULL
    AND error_result_reason IN ('resourcesExceeded', 'timeout', 'responseTooLarge')
ORDER BY total_slot_ms DESC;