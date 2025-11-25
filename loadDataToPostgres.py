import json
import os
import pandas as pd
import numpy as np
from .db_config import get_db_url_and_schema, get_instance_id
from google.cloud.sql.connector import Connector
import sqlalchemy
import pg8000

# --- Config ---
instance_conn, database, iam_user, schema, parquet_folder, ip_type = get_db_url_and_schema()

connector = Connector()

def getconn():
    conn = connector.connect(
        instance_conn,
        "pg8000",
        user=iam_user,
        db=database,
        enable_iam_auth=True,
        ip_type=ip_type,
    )
    return conn
engine = sqlalchemy.create_engine(
    "postgresql+pg8000://",
    creator=getconn,
)

# --- Function to ensure table name length limit ---
def get_safe_table_name(file_name):
    # Remove extension like .parquet if present
    file_name = file_name.replace('.parquet', '')

    # Split on underscores
    parts = file_name.split('.')
    # print(parts)
    base_name = parts[0]  # Remove any file extension if present
    
    return base_name
    
# tables_names = []
# for _ in (os.listdir(parquet_folder)):
#     tables_names.append(get_safe_table_name(_))

# # print(tables_names)

def run_ddl_scripts(connector, instance_conn, database, iam_user, schema, ip_type):
    """Run DDL scripts from info_schema_ddl_scripts folder"""
    ddl_folder = os.path.join(os.path.dirname(__file__), 'info_schema_ddl_scripts')
    
    if not os.path.exists(ddl_folder):
        print(f"[WARN] DDL folder not found: {ddl_folder}")
        return
    
    sql_files = sorted([f for f in os.listdir(ddl_folder) if f.endswith('.sql')])
    
    if not sql_files:
        print(f"[WARN] No SQL files found in {ddl_folder}")
        return
    
    print(f"\n[INFO] Running {len(sql_files)} DDL script(s) from info_schema_ddl_scripts/")
    
    instance_id = get_instance_id()
    
    for sql_file in sql_files:
        file_path = os.path.join(ddl_folder, sql_file)
        print(f"  Executing: {sql_file}")
        
        try:
            with open(file_path, 'r') as f:
                sql_content = f.read()
            
            # Replace placeholders
            sql_content = sql_content.format(schema=schema, instance=instance_id, version=1)
            
            # Execute using pg8000 directly
            conn = connector.connect(
                instance_conn,
                "pg8000",
                user=iam_user,
                db=database,
                enable_iam_auth=True,
                ip_type=ip_type,
            )
            
            cursor = conn.cursor()
            cursor.execute(sql_content)
            conn.commit()
            cursor.close()
            conn.close()
            
            print(f"    [OK] {sql_file} executed successfully")
            
        except Exception as e:
            print(f"    [ERROR] Failed to execute {sql_file}: {e}")
            raise

def serialize_complex_columns(df):
    for col in df.columns:
        if df[col].apply(lambda x: isinstance(x, (dict, list, np.ndarray))).any():
            def safe_serialize(x):
                try:
                    if isinstance(x, np.ndarray):
                        return json.dumps(x.tolist())
                    elif isinstance(x, (dict, list)):
                        return json.dumps(x)
                    else:
                        return x
                except (TypeError, ValueError):
                    return None
            df[col] = df[col].apply(safe_serialize)
    return df


# --- Process All Parquet Files ---
try:
    print(f"Connecting to Cloud SQL PostgreSQL")
    print(f"Loading data from: {parquet_folder}")
    print(f"Target schema: {schema}")
    
    # Run DDL scripts first to create tables
    run_ddl_scripts(connector, instance_conn, database, iam_user, schema, ip_type)
    
    print(f"\n[INFO] Loading parquet files...")
    for file in os.listdir(parquet_folder):
        if file.endswith('.parquet'):
            file_path = os.path.join(parquet_folder, file)
            print(f"\nReading {file_path}...")
            
            # Read Parquet file
            df = pd.read_parquet(file_path)
            df = serialize_complex_columns(df)
            table_name = get_safe_table_name(file)

            # Insert data using pandas (tables should already exist from DDL script)
            if len(df) > 0:
                df.to_sql(table_name, engine, schema=schema, if_exists='append', index=False)
                print(f"  [OK] Loaded {len(df)} rows into table: {schema}.{table_name}")
            else:
                print(f"  [WARN] No data to load for: {schema}.{table_name}")

    print("\n[SUCCESS] All parquet files loaded successfully!")

except Exception as e:
    print(f"\n[ERROR] {e}")
    raise

finally:
    engine.dispose()
    connector.close()
    print("Connection closed.")