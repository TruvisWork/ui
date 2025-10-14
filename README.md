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
