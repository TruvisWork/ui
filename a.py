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
import json

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
        
        job_project_id = config.get(section, 'project_id')
        dataset_id = config.get(section, 'dataset_id')
        job_region = config.get(section, 'region')  
        
        target_project_id = config.get(section, 'target_project_id')  
        target_region = config.get(section, 'target_region') 
        target_dataset_id = config.get(section, 'target_dataset_id') 
        
        output_dir = config.get(section, 'output_directory')
        service_account_file = config.get(section, 'service_account_file')
        
        temp_location = config.get(section, 'temp_location', fallback='US')

        credentials = service_account.Credentials.from_service_account_file(service_account_file)
        
        job_client = bigquery.Client(credentials=credentials, project=job_project_id)
        
        target_client = bigquery.Client(credentials=credentials, project=target_project_id)

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

def load_queries(filepath):
    try:
        with open(filepath, 'r') as file:
            return yaml.safe_load(file)
    except yaml.YAMLError as e:
        logging.error(f"Error loading queries from {filepath}: {e}")
        raise ValueError(f"Invalid YAML file: {filepath}")

def calculate_cost(bytes_processed):
    """
    Calculate BigQuery cost based on bytes processed
    On-demand pricing: $5 per TB (as of 2024)
    """
    if bytes_processed is None or bytes_processed == 0:
        return 0
    
    tb_processed = bytes_processed / (1024**4)
    cost_usd = tb_processed * 5  
    return cost_usd

def extract_table_name(sql_text):
    try:
        match = re.search(
            r"FROM\s+[\w-]+\.[\w-]+\.(__TABLES__)", sql_text, re.IGNORECASE
        )
        if match:
            return match.group(1)
        match = re.search(
            r"FROM\s+`?[\w-]+`?\.`?[\w-]+`?\.INFORMATION_SCHEMA\.([\w]+)", sql_text, re.IGNORECASE
        )
        if match:
            return match.group(1)
        match = re.search(
            r"FROM\s+[\w-]+\.[\w-]+\.INFORMATION_SCHEMA\.([\w]+)", sql_text, re.IGNORECASE
        )
        if match:
            return match.group(1)
        match = re.search(
            r"FROM\s+INFORMATION_SCHEMA\.([\w]+)", sql_text, re.IGNORECASE
        )
        if match:
            return match.group(1)
        return "unknown_table_name"
    except Exception as e:
        logging.error(f"Error extracting table name from SQL: {e}")
        return "unknown_table_name"

def run_query_with_temp_dataset(job_client, target_client, query, temp_location, job_project_id, table_name="unknown"):
    """Run cross-region query using temporary dataset approach with cost logging"""
    try:
        job_config = bigquery.QueryJobConfig()
        job_config.use_query_cache = False
        
        query_job = job_client.query(query, job_config=job_config)
        
        result = query_job.result()
        
        df = result.to_dataframe()
        
        job_stats = query_job._properties.get('statistics', {})
        query_stats = job_stats.get('query', {})
        
        bytes_processed = query_stats.get('totalBytesProcessed')
        bytes_billed = query_stats.get('totalBytesBilled')
        slot_ms = query_stats.get('totalSlotMs')
        cache_hit = query_stats.get('cacheHit', False)
        
        cost = calculate_cost(int(bytes_processed)) if bytes_processed else 0

        cost_info = {
            'timestamp': datetime.now().isoformat(),
            'table_name': table_name,
            'bytes_processed': int(bytes_processed) if bytes_processed else 0,
            'estimated_cost_usd': round(cost, 6)
        }

        logging.info(f"Query for {table_name} completed:")
        logging.info(f"  - Bytes processed: {cost_info['bytes_processed']:,}")
        logging.info(f"  - Estimated cost: ${cost_info['estimated_cost_usd']:.6f}")
        
        return df, cost_info
        
    except Exception as e:
        logging.error(f"Error running cross-region query: {e}")

        try:
            logging.info("Attempting to run query from target region...")
            query_job = target_client.query(query)
            result = query_job.result()
            df = result.to_dataframe()

            job_stats = query_job._properties.get('statistics', {})
            query_stats = job_stats.get('query', {})

            bytes_processed = query_stats.get('totalBytesProcessed')
            bytes_billed = query_stats.get('totalBytesBilled')
            slot_ms = query_stats.get('totalSlotMs')
            cache_hit = query_stats.get('cacheHit', False)

            cost = calculate_cost(int(bytes_processed)) if bytes_processed else 0

            cost_info = {
                'timestamp': datetime.now().isoformat(),
                'table_name': table_name,
                'bytes_processed': int(bytes_processed) if bytes_processed else 0,
                'estimated_cost_usd': round(cost, 6)
            }
            
            return df, cost_info
            
        except Exception as e2:
            logging.error(f"Error running query from target region: {e2}")
            raise

def run_query(client, query, table_name="unknown"):
    """Standard query execution with cost logging"""
    try:
        job_config = bigquery.QueryJobConfig(
            use_query_cache=False,
            dry_run=False
        )

        query_job = client.query(query, job_config=job_config)

        df = query_job.to_dataframe()

        job_stats = query_job._properties.get('statistics', {})
        query_stats = job_stats.get('query', {})

        bytes_processed = query_stats.get('totalBytesProcessed')
        bytes_billed = query_stats.get('totalBytesBilled')
        slot_ms = query_stats.get('totalSlotMs')
        cache_hit = query_stats.get('cacheHit', False)

        cost = calculate_cost(int(bytes_processed)) if bytes_processed else 0

        cost_info = {
            'timestamp': datetime.now().isoformat(),
            'table_name': table_name,
            'bytes_processed': int(bytes_processed) if bytes_processed else 0,
            'estimated_cost_usd': round(cost, 6)
        }

        logging.info(f"Query for {table_name} completed:")
        logging.info(f"  - Bytes processed: {cost_info['bytes_processed']:,}")
        logging.info(f"  - Estimated cost: ${cost_info['estimated_cost_usd']:.6f}")
        
        return df, cost_info
        
    except Exception as e:
        logging.error(f"Error running query for {table_name}: {e}")
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

def save_cost_log(cost_data, output_dir):
    """Save cost information to JSON file with table names"""
    try:
        os.makedirs(output_dir, exist_ok=True)
        
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        
        json_filepath = os.path.join(output_dir, f"cost_by_table_{timestamp}.json")
        
        table_costs = {}
        
        for cost_info in cost_data:
            table_name = cost_info['table_name']
            if table_name not in table_costs:
                table_costs[table_name] = {
                    'table_name': table_name,
                    'total_bytes_processed': 0,
                    'total_estimated_cost_usd': 0,
                    'query_count': 0,
                    'executions': []
                }
            
            table_costs[table_name]['total_bytes_processed'] += cost_info['bytes_processed']
            table_costs[table_name]['total_estimated_cost_usd'] += cost_info['estimated_cost_usd']
            table_costs[table_name]['query_count'] += 1
            table_costs[table_name]['executions'].append({
                'timestamp': cost_info['timestamp'],
                'bytes_processed': cost_info['bytes_processed'],
                'estimated_cost_usd': cost_info['estimated_cost_usd']
            })
        
        for table_name in table_costs:
            table_costs[table_name]['total_estimated_cost_usd'] = round(
                table_costs[table_name]['total_estimated_cost_usd'], 6
            )
        
        cost_summary = list(table_costs.values())
        cost_summary.sort(key=lambda x: x['total_estimated_cost_usd'], reverse=True)
        
        output_data = {
            'summary': {
                'timestamp': datetime.now().isoformat(),
                'total_tables': len(cost_summary),
                'total_queries': sum(table['query_count'] for table in cost_summary),
                'total_bytes_processed': sum(table['total_bytes_processed'] for table in cost_summary),
                'total_estimated_cost_usd': round(sum(table['total_estimated_cost_usd'] for table in cost_summary), 6)
            },
            'cost_by_table': cost_summary
        }
        
        with open(json_filepath, 'w') as f:
            json.dump(output_data, f, indent=2)
        
        logging.info(f"Cost information saved to: {json_filepath}")
        
        return json_filepath
        
    except Exception as e:
        logging.error(f"Error saving cost log: {e}")
        raise

def format_query_for_cross_region(query, target_project_id, target_region, dataset_id=None):
    """Format query for cross-region execution"""
    formatted_query = query.replace('{region}', target_region)

    formatted_query = formatted_query.replace('{project_id}', target_project_id)
    if dataset_id:
        formatted_query = formatted_query.replace('{dataset}', dataset_id)
    
    return formatted_query

def main():
    (job_client, target_client, job_project_id, job_region, target_project_id, 
     target_region, target_datasets, output_dir, temp_location) = get_creds()

    setup_logging(verbose=True)
    logging.info(f"Job running from: {job_project_id} in {job_region}")
    logging.info(f"Extracting data from: {target_project_id} in {target_region}")
    logging.info(f"Target datasets: {target_datasets}")

    queries = load_queries(os.path.abspath(os.path.join(os.path.dirname(__file__), "queries.yaml")))

    all_cost_data = []
    total_cost = 0

    for name, query in queries.items():
        try:
            logging.info(f"Processing query: {name}")

            table_name = extract_table_name(query)
            
            if name == "query6":
                all_tables_df = []
                query_cost_data = []
                
                for dataset_id in target_datasets:
                    logging.info(f"Processing dataset: {dataset_id}")
                    formatted_query = format_query_for_cross_region(query, target_project_id, target_region, dataset_id)
                    logging.debug(f"Query: {formatted_query}")
                    
                    dataset_table_name = f"{table_name}_{dataset_id}"

                    df, cost_info = run_query(target_client, formatted_query, dataset_table_name)
                    all_tables_df.append(df)
                    query_cost_data.append(cost_info)
                    all_cost_data.append(cost_info)
                
                if all_tables_df:
                    combined_df = pd.concat(all_tables_df, ignore_index=True)
                    filepath = save_to_parquet(
                        combined_df, output_dir, target_project_id, 
                        target_region, 'ALL_DATASETS', table_name
                    )
                    
                    query_total_cost = sum(cost['estimated_cost_usd'] for cost in query_cost_data)
                    total_cost += query_total_cost
                    
                    logging.info(f"Successfully saved {len(combined_df)} rows to: {filepath}")
                    logging.info(f"File size: {os.path.getsize(filepath) / (1024 * 1024):.2f} MB")
                    logging.info(f"Total cost for {table_name}: ${query_total_cost:.6f}")
                else:
                    logging.warning(f"No {table_name} data found")
            
            else:
                formatted_query = format_query_for_cross_region(query, target_project_id, target_region)
                logging.debug(f"Query: {formatted_query}")
                
                df, cost_info = run_query_with_temp_dataset(
                    job_client, target_client, formatted_query, temp_location, job_project_id, table_name
                )
                all_cost_data.append(cost_info)
                total_cost += cost_info['estimated_cost_usd']
                
                if not df.empty:
                    filepath = save_to_parquet(
                        df, output_dir, target_project_id, 
                        target_region, 'PROJECT_WIDE', table_name
                    )
                    logging.info(f"Successfully saved {len(df)} rows to: {filepath}")
                    logging.info(f"File size: {os.path.getsize(filepath) / (1024 * 1024):.2f} MB")
                else:
                    logging.warning(f"No data found for {table_name}")

        except Exception as e:
            logging.error(f"Error processing {name}: {str(e)}")
            traceback.print_exc()

    if all_cost_data:
        save_cost_log(all_cost_data, output_dir)
        
        logging.info("\n" + "="*50)
        logging.info("COST SUMMARY")
        logging.info("="*50)
        logging.info(f"Total queries executed: {len(all_cost_data)}")
        logging.info(f"Total estimated cost: ${total_cost:.6f}")
        logging.info("\nCost by table:")
        
        table_summary = {}
        for cost_info in all_cost_data:
            table_name = cost_info['table_name']
            if table_name not in table_summary:
                table_summary[table_name] = 0
            table_summary[table_name] += cost_info['estimated_cost_usd']
        
        sorted_tables = sorted(table_summary.items(), key=lambda x: x[1], reverse=True)
        for table_name, cost in sorted_tables:
            logging.info(f"  {table_name}: ${cost:.6f}")
        logging.info("="*50)

if __name__ == "__main__":
    main()
