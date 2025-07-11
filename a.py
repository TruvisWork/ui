import os
import argparse
import logging
from google.cloud import bigquery
from google.oauth2 import service_account
from datetime import datetime
import re
import traceback
import configparser
import yaml
import pandas as pd

def load_config():
    parser = argparse.ArgumentParser()
    parser.add_argument('--config-path', default=None)
    args, _ = parser.parse_known_args()

    env_config_path = os.environ.get('CONFIG_PATH')
    default_path = os.path.abspath('./extraction_utility/config/config.ini')

    config_path = args.config_path or env_config_path or default_path
    if config_path and not os.path.exists(config_path):
        config_path = default_path

    if not os.path.exists(config_path):
        raise FileNotFoundError(f"Config file not found: {config_path}")

    return config_path

def get_creds():
    """Get BigQuery project, region, dataset, output_dir information from config"""
    config_path = load_config()
    config = configparser.ConfigParser()
    config.read(config_path)
    try:
        section = 'extraction_utility'
        
        # Job execution settings (where the job runs from)
        job_project_id = config.get(section, 'job_project_id')  # asia-east2 project
        job_region = config.get(section, 'job_region')  # asia-east2
        
        # Target data settings (where the data resides)
        target_project_id = config.get(section, 'target_project_id')  # europe-west2 project
        target_region = config.get(section, 'target_region')  # europe-west2
        target_dataset_id = config.get(section, 'target_dataset_id')  # target dataset
        
        # Common settings
        output_dir = config.get(section, 'output_directory')
        service_account_file = config.get(section, 'service_account_file')
        
        # Multi-region location for temporary operations
        temp_location = config.get(section, 'temp_location', fallback='US')

        credentials = service_account.Credentials.from_service_account_file(service_account_file)
        
        # Client for job execution (will be used for running queries)
        job_client = bigquery.Client(credentials=credentials, project=job_project_id)
        
        # Client for target data access
        target_client = bigquery.Client(credentials=credentials, project=target_project_id)

        # Parse target datasets
        if not target_dataset_id:
            target_datasets = [d.dataset_id for d in target_client.list_datasets(project=target_project_id)]
        else:
            target_datasets = [d.strip() for d in target_dataset_id.split(',') if d.strip()]

        return (job_client, target_client, job_project_id, job_region, target_project_id, 
                target_region, target_datasets, output_dir, temp_location)

    except configparser.NoOptionError as e:
        raise ValueError(f"Missing configuration option: {e}")

def setup_logging(verbose):
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(format="%(asctime)s - %(levelname)s - %(message)s", level=level)

def create_cross_region_queries():
    """Create queries that work across regions using explicit project.region syntax"""
    return {
        'query1': """
            SELECT catalog_name, schema_name, schema_owner, creation_time, last_modified_time, location 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.SCHEMATA
        """,
        'query2': """
            SELECT creation_time, a.project_id, project_number, user_email, job_id, job_type, parent_job_id, 
                   session_info, statement_type, start_time, end_time, query, state, reservation_id, 
                   total_bytes_processed, a.total_slot_ms, total_modified_partitions, total_bytes_billed, 
                   error_result.reason AS error_result_reason, 
                   error_result.location AS error_result_location, 
                   error_result.debug_info AS error_result_debug_info, 
                   error_result.message AS error_result_message, 
                   cache_hit, 
                   destination_table.project_id AS destination_project_id, 
                   destination_table.dataset_id AS destination_dataset_id, 
                   destination_table.table_id AS destination_table_id, 
                   referenced_tables, labels, timeline, job_stages 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.JOBS_BY_PROJECT AS a 
            WHERE creation_time > TIMESTAMP_SUB(CURRENT_TIMESTAMP(), INTERVAL 1 DAY)
        """,
        'query3': """
            SELECT SPECIFIC_CATALOG, SPECIFIC_SCHEMA, SPECIFIC_NAME, ROUTINE_CATALOG, ROUTINE_SCHEMA, 
                   ROUTINE_NAME, ROUTINE_TYPE, DATA_TYPE, ROUTINE_BODY, ROUTINE_DEFINITION,
                   EXTERNAL_LANGUAGE, IS_DETERMINISTIC, SECURITY_TYPE, CREATED, LAST_ALTERED, DDL 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.ROUTINES
        """,
        'query4': """
            SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, COLUMN_NAME, ORDINAL_POSITION, IS_NULLABLE, 
                   DATA_TYPE, IS_GENERATED, GENERATION_EXPRESSION, IS_STORED, IS_HIDDEN, IS_UPDATABLE, 
                   IS_SYSTEM_DEFINED, IS_PARTITIONING_COLUMN, CLUSTERING_ORDINAL_POSITION 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = '{dataset}'
        """,
        'query5': """
            SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION, CHECK_OPTION, USE_STANDARD_SQL 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.VIEWS
            WHERE TABLE_SCHEMA = '{dataset}'
        """,
        'query6': """
            SELECT project_id, dataset_id, table_id, creation_time, last_modified_time, 
                   row_count, size_bytes, type 
            FROM `{target_project_id}`.{dataset}.__TABLES__
        """,
        'query7': """
            SELECT TABLE_CATALOG, TABLE_SCHEMA, TABLE_NAME, TABLE_TYPE, IS_INSERTABLE_INTO, IS_TYPED, 
                   CREATION_TIME, DDL 
            FROM `{target_project_id}`.{target_region}.INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = '{dataset}'
        """
    }

def run_query_with_temp_dataset(job_client, target_client, query, temp_location, job_project_id):
    """Run cross-region query using temporary dataset approach"""
    try:
        # For cross-region queries, we'll create a job in a multi-region location
        job_config = bigquery.QueryJobConfig()
        job_config.use_query_cache = False
        
        # Run the query from the job client
        query_job = job_client.query(query, job_config=job_config)
        
        # Wait for the query to complete
        result = query_job.result()
        
        # Convert to DataFrame
        df = result.to_dataframe()
        
        return df
    except Exception as e:
        logging.error(f"Error running cross-region query: {e}")
        
        # If cross-region fails, try running from target region
        try:
            logging.info("Attempting to run query from target region...")
            return target_client.query(query).to_dataframe()
        except Exception as e2:
            logging.error(f"Error running query from target region: {e2}")
            raise

def run_query(client, query):
    """Standard query execution"""
    try:
        return client.query(query).to_dataframe()
    except Exception as e:
        logging.error(f"Error running query: {e}")
        raise

def save_to_parquet(df, output_dir, project_id, region, dataset, name):
    try:
        dataset_path = output_dir
        os.makedirs(dataset_path, exist_ok=True)

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"{name}_{project_id}_{region}_{dataset}_{timestamp}.parquet"
        filepath = os.path.join(dataset_path, filename)

        df.to_parquet(filepath, index=False, compression='snappy')
        return filepath
    except Exception as e:
        logging.error(f"Error saving to parquet: {e}")
        raise

def extract_table_name(sql_text):
    try:
        # Try to extract table name from FROM clause
        match = re.search(r"FROM\s+`?([\w\-\.]+\.){0,2}([\w\-]+)`?", sql_text, re.IGNORECASE)
        if match:
            return match.group(2)
        
        # Try to extract from INFORMATION_SCHEMA
        match = re.search(r"INFORMATION_SCHEMA\.(\w+)", sql_text, re.IGNORECASE)
        if match:
            return match.group(1).lower()
        
        return "unknown_table"
    except Exception as e:
        logging.error(f"Error extracting table name from SQL: {e}")
        return "unknown_table"

def main():
    (job_client, target_client, job_project_id, job_region, target_project_id, 
     target_region, target_datasets, output_dir, temp_location) = get_creds()

    setup_logging(verbose=True)
    logging.info(f"Job running from: {job_project_id} in {job_region}")
    logging.info(f"Extracting data from: {target_project_id} in {target_region}")
    logging.info(f"Target datasets: {target_datasets}")

    queries = create_cross_region_queries()

    for name, query in queries.items():
        try:
            logging.info(f"Processing query: {name}")
            
            if name == "query6":
                # Handle __TABLES__ query for each dataset
                all_tables_df = []
                for dataset_id in target_datasets:
                    logging.info(f"Processing dataset: {dataset_id}")
                    formatted_query = query.format(
                        target_project_id=target_project_id,
                        dataset=dataset_id
                    )
                    logging.debug(f"Query: {formatted_query}")
                    
                    # Use target client for __TABLES__ as it's dataset-specific
                    df = run_query(target_client, formatted_query)
                    all_tables_df.append(df)
                
                if all_tables_df:
                    combined_df = pd.concat(all_tables_df, ignore_index=True)
                    table_name = extract_table_name(formatted_query)
                    filepath = save_to_parquet(
                        combined_df, output_dir, target_project_id, 
                        target_region, 'ALL_DATASETS', table_name
                    )
                    logging.info(f"Successfully saved {len(combined_df)} rows to: {filepath}")
                else:
                    logging.warning("No __TABLES__ data found")
            
            elif name in ["query4", "query5", "query7"]:
                # Handle dataset-specific INFORMATION_SCHEMA queries
                for dataset_id in target_datasets:
                    logging.info(f"Processing dataset: {dataset_id}")
                    formatted_query = query.format(
                        target_project_id=target_project_id,
                        target_region=target_region,
                        dataset=dataset_id
                    )
                    logging.debug(f"Query: {formatted_query}")
                    
                    # Try cross-region query first, fallback to target region
                    df = run_query_with_temp_dataset(
                        job_client, target_client, formatted_query, temp_location, job_project_id
                    )
                    
                    if not df.empty:
                        table_name = extract_table_name(formatted_query)
                        filepath = save_to_parquet(
                            df, output_dir, target_project_id, 
                            target_region, dataset_id, table_name
                        )
                        logging.info(f"Successfully saved {len(df)} rows to: {filepath}")
                        logging.info(f"File size: {os.path.getsize(filepath) / (1024 * 1024):.2f} MB")
                    else:
                        logging.warning(f"No data found for {name} in dataset {dataset_id}")
            
            else:
                # Handle project-wide INFORMATION_SCHEMA queries
                formatted_query = query.format(
                    target_project_id=target_project_id,
                    target_region=target_region
                )
                logging.debug(f"Query: {formatted_query}")
                
                # Try cross-region query first, fallback to target region
                df = run_query_with_temp_dataset(
                    job_client, target_client, formatted_query, temp_location, job_project_id
                )
                
                if not df.empty:
                    table_name = extract_table_name(formatted_query)
                    filepath = save_to_parquet(
                        df, output_dir, target_project_id, 
                        target_region, 'PROJECT_WIDE', table_name
                    )
                    logging.info(f"Successfully saved {len(df)} rows to: {filepath}")
                    logging.info(f"File size: {os.path.getsize(filepath) / (1024 * 1024):.2f} MB")
                else:
                    logging.warning(f"No data found for {name}")

        except Exception as e:
            logging.error(f"Error processing {name}: {str(e)}")
            traceback.print_exc()

if __name__ == "__main__":
    main()
