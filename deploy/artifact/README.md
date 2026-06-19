# Artifact Deployment

This deployment mode uploads built artifacts instead of source code.

Server directory:

```text
/opt/sc-edupivot/
  .env
  docker-compose.artifact.yaml
  jars/
  frontend/dist/
  deploy/artifact/frontend-nginx.conf
  deploy/artifact/java-runtime.Dockerfile
  deploy/nginx/edupivot.conf
  nacos-config/
  nacos-plugins/
  postgres-init/
  scripts/nacos_config_init.py
```

From this repository root on Windows:

```bat
deploy_artifact.bat infra
deploy_artifact.bat sc-course
deploy_artifact.bat frontend
deploy_artifact.bat all
```

Double-click `deploy_artifact.bat` to open the menu. Run `all` for the first deployment. Use single-service targets for later updates.

Set `EDUPIVOT_BIND_HOST=0.0.0.0` in `.env` when server ports should be reachable from outside the host. Use firewall or cloud security-group rules to restrict sensitive ports such as PostgreSQL, Redis, Nacos, Kafka, and MinIO to trusted IP addresses.

Backend service targets:

```text
sc-auth
sc-notification
sc-course
sc-storage
sc-ai
sc-gateway
all-backend
```

Backend builds use `mvn -pl <service> -am package`, so changed `sc-common` modules are rebuilt with the selected service. If a common change must reach every running service, deploy `all-backend`.

Override the server in cmd:

```bat
set DEPLOY_HOST=1.2.3.4
set DEPLOY_USER=root
set DEPLOY_DIR=/opt/sc-edupivot
deploy_artifact.bat all
```

Or create a private `deploy_artifact.local.bat` next to `deploy_artifact.bat`:

```bat
set "DEPLOY_HOST=117.72.194.197"
set "DEPLOY_USER=root"
set "DEPLOY_PORT=22"
set "DEPLOY_DIR=/opt/sc-edupivot"
set "DEPLOY_PASSWORD=your-server-password"
```

When `DEPLOY_PASSWORD` is set, the script uses PuTTY `plink` and `pscp`, so both commands must be in `PATH`. The private local file is ignored by git.

Skip rebuilds and only upload existing artifacts:

```bat
set SKIP_BUILD=1
deploy_artifact.bat sc-course
```

The POSIX shell version is also available at `scripts/deploy_artifact.sh`.
