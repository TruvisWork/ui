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

