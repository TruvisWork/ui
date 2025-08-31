SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_multiple_updates_on_a_single_table
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    user_name TEXT,
    project_name TEXT,
    log_id TEXT,
    query TEXT,
    left_database TEXT,
    schema_name TEXT,
    left_table TEXT,
    left_table_type TEXT,
    left_table_size DOUBLE PRECISION,
    sorted_right_table_set TEXT,
    update_count BIGINT,
    cost DOUBLE PRECISION;

INSERT INTO rule_multiple_updates_on_a_single_table
SELECT
    8 AS rule_id,
    'hsbc' AS org_id,
    tbl_q.version_id,
    tbl_q.user_name,
    left_database AS project_name,
    tbl_q.log_id,
    sql_query as query,
    tbl_q.left_database,
    left_schema as schema_name,
    tbl_q.left_table,
    tbl_q.left_table_type,
    ROUND(l_t.size_mb, 3) AS left_table_size,
    tbl_q.sorted_right_table_set,
    tbl_q.update_count,
    ROUND(
        CEIL(
            ((l_t.size_mb) * (update_count - 1)) * 88.0 / 60.0
        ) * ROUND(COALESCE(0.06, 0.001), 3)
    )::DOUBLE PRECISION AS cost
FROM (
    SELECT
        base.version_id,
        base.user_name,
        base.log_id,
        base.sql_query,
        base.left_database,
        base.left_schema,
        base.left_table,
        base.left_table_type,
        base.sorted_right_table_set,
        COUNT(*) OVER (PARTITION BY left_database, left_schema, left_table) AS update_count
    FROM (
        SELECT
            s.version_id,
            s.user_name,
            s.log_id,
            q.sql_query,
            er.target_database AS left_database,
            er.target_schema AS left_schema,
            er.target_entity_name AS left_table,
            er.target_entity_type AS left_table_type,
            STRING_AGG(
                CASE
                    WHEN er.source_entity_type = 'USER' THEN CONCAT(er.source_entity_type, ':', er.source_entity_name)
                    ELSE CONCAT(er.source_entity_type, ':', er.source_database, '.', er.source_schema, '.', er.source_entity_name)
                END,
                ',' ORDER BY er.source_entity_type, er.source_database, er.source_schema, er.source_entity_name
            ) AS sorted_right_table_set
        FROM sql_statement_info s
        JOIN entity_relationship er ON s.id = er.sql_statement_info_id
        JOIN query_log q ON q.log_id = s.log_id
        WHERE
            s.version_id = {version} AND
            (er.target_entity_type IN ('TABLE', 'VIEW')
            AND er.source_entity_type IN ('TABLE', 'VIEW', 'USER')
            AND er.relationship_type = 'DEPENDS_ON'
            AND statement_type = 'UPDATE')
        GROUP BY
            s.version_id,
            s.user_name,
            s.log_id,
            q.sql_query,
            er.target_database,
            er.target_schema,
            er.target_entity_name,
            er.target_entity_type
    ) base
) tbl_q
JOIN table_metadata l_t
    ON tbl_q.left_database = l_t.database
    AND tbl_q.left_schema = l_t.schema
    AND tbl_q.left_table = l_t.table_name
WHERE l_t.version_id = {version} AND update_count > 1;