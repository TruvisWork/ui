import os
import argparse
from datetime import datetime
from google.cloud.sql.connector import Connector
import pg8000
from .db_config import get_db_url_and_schema, get_instance_id
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class VersionMaster:
    """
    Version Master class to track pipeline execution status and stages
    """

    def __init__(self):
        self.version_id = None
        self.project_ids = []
        self.extraction_date = None
        self.conn = None
        self.connector = None

    def get_db_connection(self):
        """Establish a connection to Cloud SQL PostgreSQL using IAM authentication"""
        try:
            instance_connection_name, database, iam_user, schema, input_dir, ip_type = get_db_url_and_schema()
            self.connector = Connector()
            self.conn = self.connector.connect(
                instance_connection_name,
                "pg8000",
                user=iam_user,
                db=database,
                enable_iam_auth=True,
                ip_type=ip_type,
            )
            return True
        except Exception as e:
            logger.error(f"Error: Unable to connect to the database. {e}")
            return False

    def close_connection(self):
        """Close database connection"""
        try:
            if self.conn:
                self.conn.close()
            if self.connector:
                self.connector.close()
        except Exception as e:
            logger.error(f"Error closing connection: {e}")

    def create_version_master_table(self):
        """Create version_master table if it doesn't exist"""
        try:
            if not self.conn and not self.get_db_connection():
                return False

            cursor = self.conn.cursor()
            _, _, _, schema, _, _ = get_db_url_and_schema()

            create_table_query = f"""
            CREATE TABLE IF NOT EXISTS {schema}.version_master (
                version_id BIGINT PRIMARY KEY,
                project_ids TEXT NOT NULL,
                extraction_date TIMESTAMP NOT NULL,
                status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING', 'IN_PROGRESS', 'COMPLETED', 'FAILED')),
                stage_name VARCHAR(100) NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP
            );

            CREATE INDEX IF NOT EXISTS idx_version_master_extraction_date ON {schema}.version_master(extraction_date DESC);
            CREATE INDEX IF NOT EXISTS idx_version_master_status ON {schema}.version_master(status);
            CREATE INDEX IF NOT EXISTS idx_version_master_project_ids ON {schema}.version_master(project_ids);
            """

            cursor.execute(create_table_query)
            self.conn.commit()
            cursor.close()
            logger.info("Version master table created/verified successfully")
            return True

        except Exception as e:
            logger.error(f"Error creating version master table: {e}")
            if self.conn: self.conn.rollback()
            return False

    def get_next_version_id(self):
        """Get the next version ID across all projects"""
        try:
            if not self.conn and not self.get_db_connection():
                return 123  # Start from 123 if no connection

            cursor = self.conn.cursor()
            _, _, _, schema, _, _ = get_db_url_and_schema()

            query = f"SELECT MAX(version_id) FROM {schema}.version_master"
            cursor.execute(query)
            result = cursor.fetchone()
            cursor.close()

            if result and result[0] is not None:
                next_version_id = int(result[0]) + 1
            else:
                # Table is empty, start from 123
                next_version_id = 123

            logger.info(f"Next global version ID: {next_version_id}")
            return next_version_id

        except Exception as e:
            logger.error(f"Error getting next version ID: {e}")
            return 123  # Default to 123 if there's an error

    def initialize_version(self, project_ids_list):
        """Initialize a new version entry for the pipeline run with a list of project IDs."""
        try:
            if not self.conn and not self.get_db_connection():
                return None

            if not self.create_version_master_table():
                return None

            self.version_id = self.get_next_version_id()
            self.project_ids = project_ids_list
            project_ids_str = ','.join(project_ids_list)
            self.extraction_date = datetime.now()

            cursor = self.conn.cursor()
            _, _, _, schema, _, _ = get_db_url_and_schema()

            insert_query = f"""
            INSERT INTO {schema}.version_master 
            (version_id, project_ids, extraction_date, status, stage_name, start_time)
            VALUES (%s, %s, %s, %s, %s, %s)
            """

            cursor.execute(insert_query, (
                self.version_id,
                project_ids_str,
                self.extraction_date,
                'RUNNING',
                'Pipeline Initialization',
                datetime.now()
            ))

            self.conn.commit()
            cursor.close()

            logger.info(f"Version initialized: {self.version_id} for projects: {project_ids_str}")
            return self.version_id

        except Exception as e:
            logger.error(f"Error initializing version: {e}")
            if self.conn: self.conn.rollback()
            return None

    def append_project_id(self, new_project_id):
        """Append a new project ID to the current version's project list"""
        try:
            if not self.conn or not self.version_id:
                logger.error("No active version or connection")
                return False

            # Add to local list if not already present
            if new_project_id not in self.project_ids:
                self.project_ids.append(new_project_id)
                
                cursor = self.conn.cursor()
                _, _, _, schema, _, _ = get_db_url_and_schema()

                project_ids_str = ','.join(self.project_ids)
                update_query = f"UPDATE {schema}.version_master SET project_ids = %s WHERE version_id = %s"
                
                cursor.execute(update_query, (project_ids_str, self.version_id))
                self.conn.commit()
                cursor.close()

                logger.info(f"Appended project ID {new_project_id} to version {self.version_id}")
                return True
            else:
                logger.info(f"Project ID {new_project_id} already exists in version {self.version_id}")
                return True

        except Exception as e:
            logger.error(f"Error appending project ID: {e}")
            if self.conn: self.conn.rollback()
            return False

    def update_status(self, stage_name, status='IN_PROGRESS', error_message=None):
        """Update the status and stage of the current version using only version_id."""
        try:
            if not self.conn or not self.version_id:
                logger.error("No active version or connection")
                return False

            cursor = self.conn.cursor()
            _, _, _, schema, _, _ = get_db_url_and_schema()

            params = []
            if status in ['COMPLETED', 'FAILED']:
                update_query = f"UPDATE {schema}.version_master SET status = %s, stage_name = %s, end_time = %s WHERE version_id = %s"
                params = [status, stage_name, datetime.now(), self.version_id]
            else:
                update_query = f"UPDATE {schema}.version_master SET status = %s, stage_name = %s WHERE version_id = %s"
                params = [status, stage_name, self.version_id]

            cursor.execute(update_query, tuple(params))
            self.conn.commit()
            cursor.close()

            logger.info(f"Status updated: {status} - {stage_name} for version {self.version_id}")
            return True

        except Exception as e:
            logger.error(f"Error updating status: {e}")
            if self.conn: self.conn.rollback()
            return False

    def get_latest_version_info(self, project_id=None, status_filter='COMPLETED'):
        """Get the latest version that includes a specific project_id."""
        try:
            if not self.conn and not self.get_db_connection():
                return None, None

            cursor = self.conn.cursor()
            _, _, _, schema, _, _ = get_db_url_and_schema()

            query = f"SELECT version_id, extraction_date FROM {schema}.version_master"
            params = []
            conditions = []
            if project_id:
                conditions.append("project_ids LIKE %s")
                params.append(f"%{project_id}%")
            if status_filter:
                conditions.append("status = %s")
                params.append(status_filter)

            if conditions:
                query += " WHERE " + " AND ".join(conditions)

            query += " ORDER BY version_id DESC LIMIT 1"

            cursor.execute(query, tuple(params))
            result = cursor.fetchone()
            cursor.close()

            if result:
                logger.info(f"Latest version found: {result[0]}")
                return result[0], result[1]
            else:
                logger.warning(f"No version found for project '{project_id}' with status '{status_filter}'")
                return None, None

        except Exception as e:
            logger.error(f"Error getting latest version info: {e}")
            return None, None

    def get_current_version_info(self):
        """Get current version information"""
        if self.version_id and self.extraction_date and self.project_ids:
            return self.version_id, self.extraction_date, ','.join(self.project_ids)
        return None, None, None  # Should be initialized first

    def mark_step_completed(self, step_number, stage_name):
        status = 'IN_PROGRESS' if step_number < 7 else 'COMPLETED'
        return self.update_status(stage_name, status)

    def mark_step_failed(self, step_number, stage_name, error_message):
        return self.update_status(stage_name, 'FAILED', error_message)


# Global instance
_version_master_instance = None

def get_version_master():
    global _version_master_instance
    if _version_master_instance is None:
        _version_master_instance = VersionMaster()
    return _version_master_instance

def initialize_pipeline_version(project_ids_list):
    """Initialize pipeline version with list of project IDs"""
    if isinstance(project_ids_list, str):
        project_ids_list = [pid.strip() for pid in project_ids_list.split(',')]
    
    vm = get_version_master()
    return vm.initialize_version(project_ids_list)

def append_pipeline_project_id(project_id):
    """Append a project ID to current pipeline version"""
    vm = get_version_master()
    return vm.append_project_id(project_id)

def mark_pipeline_step_completed(step_number, stage_name):
    vm = get_version_master()
    return vm.mark_step_completed(step_number, stage_name)

def mark_pipeline_step_failed(step_number, stage_name, error_message):
    vm = get_version_master()
    return vm.mark_step_failed(step_number, stage_name, error_message)

def get_latest_pipeline_version(project_id="default_project", status_filter='COMPLETED'):
    vm = get_version_master()
    return vm.get_latest_version_info(project_id, status_filter)

def get_current_pipeline_version():
    vm = get_version_master()
    return vm.get_current_version_info()

def get_current_version_id():
    """Get just the current version ID"""
    vm = get_version_master()
    version_id, _, _ = vm.get_current_version_info()
    return version_id

def close_version_master_connection():
    vm = get_version_master()
    vm.close_connection()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description="Version Master Utility")
    parser.add_argument("--get-version", action="store_true", help="Get the current running version ID")
    parser.add_argument("--project-ids", help="Comma-separated project IDs")
    args = parser.parse_args()

    if args.get_version:
        version_id = get_current_version_id()
        if version_id:
            print(version_id)
        else:
            exit(1)
