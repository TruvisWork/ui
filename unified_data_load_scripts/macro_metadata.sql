SET search_path to {schema};

-- Create the table
CREATE TABLE IF NOT EXISTS {schema}.macro_metadata (
  "database" TEXT,
  version_id BIGINT,
  "schema" TEXT,
  "macro_name" TEXT,
  user_name TEXT,
  sql_query TEXT,
  create_at BIGINT,
  update_at BIGINT,
  instance TEXT
);
