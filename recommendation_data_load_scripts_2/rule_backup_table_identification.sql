SET search_path to {schema};

CREATE TABLE IF NOT EXISTS rule_backup_table_identification
    rule_id BIGINT,
    org_id TEXT,
    version_id BIGINT,
    database TEXT,
    schema TEXT,
    table_name TEXT;

INSERT INTO rule_backup_table_identification
SELECT DISTINCT
    13 AS rule_id,
    'hsbc' AS org_id,
    version_id,
    database,
    schema,
    table_name
FROM table_metadata
WHERE
    version_id = {version} AND
    (LOWER(table_name) LIKE '%_backup%'
    OR LOWER(table_name) LIKE '%_bkp%'
    OR LOWER(table_name) LIKE '%backup%' 
    OR LOWER(table_name) LIKE '%bkp%' 
    OR LOWER(table_name) LIKE '%backup%' || '%[0-9]%'  -- Simulate date suffix
    OR LOWER(table_name) LIKE '%bkp%' || '%[0-9]%');