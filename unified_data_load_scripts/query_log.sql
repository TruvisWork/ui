SET search_path to {schema};

-- Create the main query log table
CREATE TABLE IF NOT EXISTS {schema}.query_log (
  source_product TEXT,
  "database" TEXT,
  schema TEXT,
  log_id TEXT,
  sql_query TEXT,
  session_id TEXT,
  version_id BIGINT,
  user_name TEXT,
  start_time BIGINT,
  total_execution_time_ms BIGINT,
  cpu_time_ms BIGINT,
  application_id TEXT,
  io_count BIGINT,
  is_stored_procedure_call BOOLEAN,
  proc_id BIGINT,
  instance TEXT,
  is_command BOOLEAN,
  command_entity_type TEXT,
  command_kind TEXT,
  command_source TEXT,
  command_destination TEXT,
  command_schema_json TEXT,
  command_create_at BIGINT,
  command_update_at BIGINT,
  command_entity_name TEXT,
  command_view_sql TEXT,
  workload_reservation_id TEXT,
  hardware_util_time_ms BIGINT
);

-- Insert transformed data
INSERT INTO {schema}.query_log (
  source_product,
  "database",
  version_id,
  schema,
  log_id,
  sql_query,
  session_id,
  user_name,
  start_time,
  total_execution_time_ms,
  cpu_time_ms,
  application_id,
  io_count,
  is_stored_procedure_call,
  proc_id,
  instance,
  is_command,
  command_entity_type,
  command_kind,
  command_source,
  command_destination,
  command_schema_json,
  command_create_at,
  command_update_at,
  command_entity_name,
  command_view_sql,
  workload_reservation_id,
  hardware_util_time_ms
)
SELECT
  'BIG_QUERY' AS source_product,
  project_id AS "database",
  {version} AS version_id,
  destination_dataset_id AS schema,
  job_id AS log_id,
  query AS sql_query,
  COALESCE(
    parent_job_id,
    (session_info::jsonb->>'session_id'),
    job_id
  ) AS session_id,
  user_email AS user_name,
  EXTRACT(EPOCH FROM start_time)::BIGINT AS start_time,
  (EXTRACT(EPOCH FROM end_time) - EXTRACT(EPOCH FROM start_time))::BIGINT AS total_execution_time_ms,
  0 AS cpu_time_ms,
  user_email AS application_id,
  total_bytes_processed,
  CASE
    WHEN parent_job_id IS NOT NULL OR (session_info::jsonb->>'session_id') IS NOT NULL THEN TRUE
    ELSE FALSE
  END AS is_stored_procedure_call,
  0 AS proc_id,
  '{instance}' AS instance,
  FALSE AS is_command,
  '' AS command_entity_type,
  '' AS command_kind,
  NULL AS command_source,
  NULL AS command_destination,
  '' AS command_schema_json,
  NULL AS command_create_at,
  NULL AS command_update_at,
  '' AS command_entity_name,
  '' AS command_view_sql,
  reservation_id,
  total_slot_ms
FROM {schema}."JOBS_BY_PROJECT"
WHERE
  state = 'DONE'
  AND (error_result_reason IS NULL OR error_result_reason = 'responseTooLarge')
  AND job_type = 'QUERY';

-- Create a deduplicated version of the query log
DROP TABLE IF EXISTS {schema}.query_log_dedupe;

CREATE TABLE {schema}.query_log_dedupe AS
SELECT *
FROM (
  SELECT *,
         ROW_NUMBER() OVER (PARTITION BY sql_query, user_name, application_id ORDER BY start_time ASC) AS row_num,
         COUNT(*) OVER (PARTITION BY sql_query, user_name, application_id) AS query_count
  FROM {schema}.query_log
) sub
WHERE row_num = 1;