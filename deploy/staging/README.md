# Staging

Staging runs on the Jenkins host, `192.168.1.100`, at **<http://192.168.1.100:8081>**.

| Piece | Where |
|-------|-------|
| Web (nginx, serves the bundle and proxies the API on the same origin) | host network, `:8081` |
| API (Spring Boot, profile `prod`) | host network, `127.0.0.1:8180` only |
| PostgreSQL | existing install, `192.168.1.100:5432/giaky`, user `postgres` |
| Uploaded media | Docker volume `giaky-staging-media` |
| Swagger UI | <http://192.168.1.100:8081/swagger-ui.html> |

Files: `api/Dockerfile`, `web/Dockerfile`, `web/deploy/nginx.conf.template`, `deploy/staging/docker-compose.yml`,
`Jenkinsfile` (pipeline), `deploy/staging/jenkins-job.xml` (the job that runs it),
`deploy/staging/seed.py` + `Jenkinsfile.seed` + `jenkins-seed-job.xml` (demo data).
Production is a separate job, `giaky-prod`: see `deploy/prod/README.md`.

## One-time Jenkins setup

1. **Credentials** (Manage Jenkins → Credentials → System → Global):
   - `giaky-staging-db-password`: *Secret text*, the `postgres` password.
   - `giaky-staging-jwt-secret`: *Secret text*, the output of `openssl rand -base64 32`.
   - `github-ssh`: *SSH username with private key*, user `git`, a key with read access to
     `hungbb/family-log` (a repository deploy key is enough).
2. **Job**: create it from the XML, using an API token from your Jenkins user page:

   ```bash
   curl -fsS -u "<user>:<api-token>" -H "Content-Type: application/xml" \
     --data-binary @deploy/staging/jenkins-job.xml \
     "http://192.168.1.100:8080/createItem?name=giaky-staging"
   ```

   The job polls `main` every ~5 minutes and reads the `Jenkinsfile` from the commit it builds.

## Pipeline

Test API (`./mvnw verify` in `eclipse-temurin:21-jdk`, with Testcontainers) → Verify web (the `verify`
stage of `web/Dockerfile`) → build both images, tagged `staging-<build>` and `staging` → `docker compose up -d`
→ wait for `/actuator/health` through nginx. Flyway migrates the `giaky` database when the API starts.

To deploy without tests, run **Build with Parameters** and untick `RUN_TESTS`.

## Demo data

`deploy/staging/seed.py` fills staging with a demo clan (about 80 people over six generations)
and four test accounts, through the public API. Run it from Jenkins with the `giaky-staging-seed`
job (`deploy/staging/Jenkinsfile.seed`), which is manual and safe to rerun: the script stops if
the demo clan already exists. Create the job once:

```bash
curl -fsS -u "<user>:<api-token>" -H "Content-Type: application/xml" \
  --data-binary @deploy/staging/jenkins-seed-job.xml \
  "http://192.168.1.100:8080/createItem?name=giaky-staging-seed"
```

Or run it from any machine with Python 3.10+: `python3 deploy/staging/seed.py`. The accounts and
password are listed at the top of the script.

## Operating it by hand

```bash
docker compose -f deploy/staging/docker-compose.yml ps
docker compose -f deploy/staging/docker-compose.yml logs -f api
# Roll back to an earlier build (the three newest are kept):
IMAGE_TAG=staging-41 DB_PASSWORD=... JWT_SECRET=... docker compose -f deploy/staging/docker-compose.yml up -d
```

There is no SMTP yet, and verification emails are logged only under the `dev` profile, so on
staging a new account stays `PENDING_VERIFICATION`. It can still sign in.
