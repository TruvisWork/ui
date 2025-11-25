-- Create INFORMATION_SCHEMA tables with proper data types matching BigQuery
-- This script should be run before loading parquet data

SET search_path to {schema};

-- Drop existing tables
DROP TABLE IF EXISTS "{schema}"."SCHEMATA" CASCADE;
DROP TABLE IF EXISTS "{schema}"."JOBS_BY_PROJECT" CASCADE;
DROP TABLE IF EXISTS "{schema}"."ROUTINES" CASCADE;
DROP TABLE IF EXISTS "{schema}"."COLUMNS" CASCADE;
DROP TABLE IF EXISTS "{schema}"."VIEWS" CASCADE;
DROP TABLE IF EXISTS "{schema}"."TABLES__" CASCADE;
DROP TABLE IF EXISTS "{schema}"."TABLES" CASCADE;

-- SCHEMATA table
CREATE TABLE IF NOT EXISTS "{schema}"."SCHEMATA" (
    catalog_name TEXT,
    schema_name TEXT,
    schema_owner TEXT,
    creation_time TIMESTAMPTZ,
    last_modified_time TIMESTAMPTZ,
    location TEXT
);

-- JOBS_BY_PROJECT table
CREATE TABLE IF NOT EXISTS "{schema}"."JOBS_BY_PROJECT" (
    creation_time TIMESTAMPTZ,
    project_id TEXT,
    project_number BIGINT,
    user_email TEXT,
    job_id TEXT,
    job_type TEXT,
    parent_job_id TEXT,
    session_info TEXT,
    statement_type TEXT,
    start_time TIMESTAMPTZ,
    end_time TIMESTAMPTZ,
    query TEXT,
    state TEXT,
    reservation_id TEXT,
    total_bytes_processed BIGINT,
    total_slot_ms BIGINT,
    total_modified_partitions BIGINT,
    total_bytes_billed BIGINT,
    error_result_reason TEXT,
    error_result_location TEXT,
    error_result_debug_info TEXT,
    error_result_message TEXT,
    cache_hit BOOLEAN,
    destination_project_id TEXT,
    destination_dataset_id TEXT,
    destination_table_id TEXT,
    referenced_tables JSONB,
    labels JSONB,
    timeline JSONB,
    job_stages JSONB
);

-- ROUTINES table
CREATE TABLE IF NOT EXISTS "{schema}"."ROUTINES" (
    "SPECIFIC_CATALOG" TEXT,
    "SPECIFIC_SCHEMA" TEXT,
    "SPECIFIC_NAME" TEXT,
    "ROUTINE_CATALOG" TEXT,
    "ROUTINE_SCHEMA" TEXT,
    "ROUTINE_NAME" TEXT,
    "ROUTINE_TYPE" TEXT,
    "DATA_TYPE" TEXT,
    "ROUTINE_BODY" TEXT,
    "ROUTINE_DEFINITION" TEXT,
    "EXTERNAL_LANGUAGE" TEXT,
    "IS_DETERMINISTIC" TEXT,
    "SECURITY_TYPE" TEXT,
    "CREATED" TIMESTAMPTZ,
    "LAST_ALTERED" TIMESTAMPTZ,
    "DDL" TEXT
);

-- COLUMNS table
CREATE TABLE IF NOT EXISTS "{schema}"."COLUMNS" (
    "TABLE_CATALOG" TEXT,
    "TABLE_SCHEMA" TEXT,
    "TABLE_NAME" TEXT,
    "COLUMN_NAME" TEXT,
    "ORDINAL_POSITION" INTEGER,
    "IS_NULLABLE" TEXT,
    "DATA_TYPE" TEXT,
    "IS_GENERATED" TEXT,
    "GENERATION_EXPRESSION" TEXT,
    "IS_STORED" TEXT,
    "IS_HIDDEN" TEXT,
    "IS_UPDATABLE" TEXT,
    "IS_SYSTEM_DEFINED" TEXT,
    "IS_PARTITIONING_COLUMN" TEXT,
    "CLUSTERING_ORDINAL_POSITION" INTEGER
);

-- VIEWS table
CREATE TABLE IF NOT EXISTS "{schema}"."VIEWS" (
    "TABLE_CATALOG" TEXT,
    "TABLE_SCHEMA" TEXT,
    "TABLE_NAME" TEXT,
    "VIEW_DEFINITION" TEXT,
    "CHECK_OPTION" TEXT,
    "USE_STANDARD_SQL" TEXT
);

-- TABLES__ table (BigQuery metadata table)
CREATE TABLE IF NOT EXISTS "{schema}"."TABLES__" (
    project_id TEXT,
    dataset_id TEXT,
    table_id TEXT,
    creation_time BIGINT,
    last_modified_time BIGINT,
    row_count BIGINT,
    size_bytes BIGINT,
    type TEXT
);

-- TABLES table
CREATE TABLE IF NOT EXISTS "{schema}"."TABLES" (
    "TABLE_CATALOG" TEXT,
    "TABLE_SCHEMA" TEXT,
    "TABLE_NAME" TEXT,
    "TABLE_TYPE" TEXT,
    "IS_INSERTABLE_INTO" TEXT,
    "IS_TYPED" TEXT,
    "CREATION_TIME" TIMESTAMPTZ,
    "DDL" TEXT
);
