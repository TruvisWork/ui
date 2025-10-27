"""
BigQuery client setup service.
"""
from google.cloud import bigquery
from google.oauth2 import service_account


from src.utils.config_reader import load_config


def get_bigquery_client(marketname: str):
    """
    Create and return a BigQuery client using the configuration in config.ini.

    Returns:
        tuple: (bigquery.Client, project_id, dataset_id)
    """
    # Read configuration
    config = load_config(marketname)

    # Get BigQuery configuration
    project_id = config.get('Database', 'bigquery_project')
    dataset_id = config.get('Database', 'bigquery_dataset')
    service_account_file = config.get('Database', 'service_account_file')

    # Create credentials from service account file
    credentials = service_account.Credentials.from_service_account_file(service_account_file)

    # Create BigQuery client
    client = bigquery.Client(credentials=credentials, project=project_id)

    return client, project_id, dataset_id