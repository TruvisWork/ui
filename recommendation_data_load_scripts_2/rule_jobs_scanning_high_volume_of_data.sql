SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_jobs_scanning_high_volume_of_data
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    project_name TEXT,
    schema_name TEXT,
    log_id TEXT,
    query TEXT,
    user_name TEXT,
    scan_size_in_gb DOUBLE PRECISION,
    total_slot_ms DOUBLE PRECISION,
    cost DOUBLE PRECISION;

INSERT INTO rule_jobs_scanning_high_volume_of_data
WITH job_stats AS (
    SELECT
        project_id AS project_name,
        job_id,
        query,
        user_email AS user_name,
        total_bytes_processed::DECIMAL / 1024.0 / 1024.0 / 1024.0 AS scan_size_in_gb,
        total_slot_ms,
        CASE
            WHEN total_slot_ms IS NULL THEN 0.0
            ELSE ROUND(
                CEIL(total_slot_ms::DECIMAL / 1000.0 / 60.0) * COALESCE(0.06, 0.001)::DECIMAL,
                3
            )::DECIMAL(10, 3)
        END AS cost
    FROM "JOBS_BY_PROJECT"
    WHERE query IS NOT NULL
    AND error_result_reason IS NULL
    AND total_bytes_processed::DECIMAL / 1024.0 / 1024.0 / 1024.0 > 20
),
unnested_queries AS (
    SELECT DISTINCT
        7 AS rule_id,
        'hsbc' AS org_id,
        {version} AS version_id,
        project_name,
        'NA' AS schema_name,
        job_id AS log_id,
        query,
        user_name,
        scan_size_in_gb,
        total_slot_ms,
        cost
    FROM job_stats
)
SELECT *
FROM unnested_queries
ORDER BY scan_size_in_gb DESC;