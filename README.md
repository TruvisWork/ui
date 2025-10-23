# How to run in local machine

```
npm install
npm run dev
```
# How to run in GCP Ubuntu VM machine

```
npm install
npm run dev -- --host 0.0.0.0
```

To start in detach mode

`nohup npm run dev -- --host 0.0.0.0 > tag-ui/app.log 2>&1 &`

# React + Vite

This template provides a minimal setup to get React working in Vite with HMR and some ESLint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react/README.md) uses [Babel](https://babeljs.io/) for Fast Refresh
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react-swc) uses [SWC](https://swc.rs/) for Fast Refresh


CREATE TABLE base_query_info_core (
version_id INTEGER NOT NULL,
s_id BIGINT,
log_id BIGINT,
statement_type TEXT,
database TEXT,
schema TEXT,
query TEXT,
target_database TEXT,
target_schema TEXT,
target_entity_name TEXT,
source_database TEXT,
relationship_type TEXT
) PARTITION BY LIST (version_id);


1. IAM Member for VM Stopper
bash
terraform import 'google_compute_instance_iam_member.vm_stopper[0]' \
hsbc-12010598-fdrasp-dev/asia-east2-a/query-genie-reco-dev-9999-test organizations/1038829057055/roles/cr.instanceStopStart serviceAccount:service-761857469903@compute-system.iam.gserviceaccount.com
Format: <project>/<zone>/<instance_name> <role> <member>

2. SQL Database (recommendations)
bash
terraform import 'google_sql_database.database[0]' \
hsbc-12010598-fdrasp-dev/qg-reco-engine-dev/recommendations
Format: <project>/<instance_name>/<database_name>

3. SQL User - Service Account query-genie@hsbc-12010598-fdrasp-dev.iam.gserviceaccount.com
bash
terraform import 'google_sql_user.iam_accounts["query-genie@hsbc-12010598-fdrasp-dev.iam.gserviceaccount.com_true"]' \
hsbc-12010598-fdrasp-dev/qg-reco-engine-dev/query-genie@hsbc-12010598-fdrasp-dev.iam
Format: <project>/<instance_name>/<user_name>

4. SQL User - Service Account terraform-jenkins-usr@hsbc-12010598-fdrasp-dev.iam.gserviceaccount.com
bash
terraform import 'google_sql_user.iam_accounts["terraform-jenkins-usr@hsbc-12010598-fdrasp-dev.iam.gserviceaccount.com_true"]' \
hsbc-12010598-fdrasp-dev/qg-reco-engine-dev/terraform-jenkins-usr@hsbc-12010598-fdrasp-dev.iam
5. SQL User - postgres_admin
bash
terraform import 'google_sql_user.postgres_admin[0]' \
hsbc-12010598-fdrasp-dev/qg-reco-engine-dev/postgres_admin
