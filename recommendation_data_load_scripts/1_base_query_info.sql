SET search_path to {schema};
CREATE OR REPLACE VIEW base_query_info AS
SELECT DISTINCT
    s.id as s_id,
    s.log_id,
    s.statement_type,
    s.database,
    s.schema,
    s.version_id,
    s.user_name,
    q.sql_query as query,
    sc.*,
    er.target_database,
    er.target_schema,
    er.target_entity_name,
    er.source_database,
    er.relationship_type,
    COALESCE(tm.size_mb, 0.0) as table_size
FROM sql_statement_info s
JOIN statement_context sc ON s.id = sc.sql_statement_info_id
JOIN query_log q ON q.log_id = s.log_id
INNER JOIN entity_relationship er ON s.id = er.sql_statement_info_id
LEFT JOIN table_metadata tm ON
    tm.database = er.target_database AND
    tm.schema = er.target_schema AND
    tm.table_name = er.target_entity_name;