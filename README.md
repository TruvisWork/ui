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


locals {
  vm_metadata = {
    region                   = var.region
    zonePostfix              = var.zone_postfix
    project                  = var.project_id
    yumpackages              = var.yumpackages
    env                      = var.env
    nexus3                   = var.nexus3
    gbmtNexus                = var.gbmtNexus
    nexus302                 = var.nexus302
    nodenpm                  = var.nodenpm
    bucketUriStartup         = var.bucketUriStartup
    cloudsql_instance_name   = var.sql_instance_name
    cloudsql_connection_string = "${var.project_id}:${var.region}:${var.sql_instance_name}"
    cloudsql_private_ip      = var.cloudsql_private_ip
    isGmiTest                = var.isGmiTest
  }
}


WITH target_dates AS (
  SELECT DATE "2025-10-04" AS d UNION ALL
  SELECT DATE "2025-10-01" UNION ALL
  SELECT DATE "2025-09-18" UNION ALL
  SELECT DATE "2025-05-08" UNION ALL
  SELECT DATE "2025-10-11" UNION ALL
  SELECT DATE "2025-09-30" UNION ALL
  SELECT DATE "2025-08-11" UNION ALL
  SELECT DATE "2025-09-15" UNION ALL
  SELECT DATE "2025-10-10" UNION ALL
  SELECT DATE "2025-09-12" UNION ALL
  SELECT DATE "2025-09-13" UNION ALL
  SELECT DATE "2025-10-03" UNION ALL
  SELECT DATE "2025-09-26" UNION ALL
  SELECT DATE "2025-10-07" UNION ALL
  SELECT DATE "2025-10-02" UNION ALL
  SELECT DATE "2025-08-13" UNION ALL
  SELECT DATE "2025-09-23"
)
SELECT *
FROM (
  SELECT *,
    ROW_NUMBER() OVER (
      PARTITION BY lifecycle_id
      ORDER BY event_occurred_at DESC
    ) AS row_num
  FROM AMH_FZ_FDR_PROD.event_store e
  JOIN target_dates t
    ON e.bq_insert_timestamp >= TIMESTAMP(t.d)
   AND e.bq_insert_timestamp < TIMESTAMP_ADD(TIMESTAMP(t.d), INTERVAL 1 DAY)
  WHERE LOWER(e.event_type) = "transfer_initiation"
)
WHERE row_num = 1;


resource "local_file" "vm_sa_key_file" {
  content  = jsonencode({
    type                     = "service_account"
    project_id               = var.project_id
    private_key_id           = google_service_account_key.vm_sa_key.private_key_id
    private_key              = google_service_account_key.vm_sa_key.private_key
    client_email             = var.vm_sa_email
    client_id                = google_service_account_key.vm_sa_key.client_id
    auth_uri                 = "https://accounts.google.com/o/oauth2/auth"
    token_uri                = "https://oauth2.googleapis.com/token"
    auth_provider_x509_cert_url = "https://www.googleapis.com/oauth2/v1/certs"
    client_x509_cert_url     = "https://www.googleapis.com/robot/v1/metadata/x509/${var.vm_sa_email}"
    universe_domain          = "googleapis.com"
  })
  filename = "${var.vm_sa_file}"
}

