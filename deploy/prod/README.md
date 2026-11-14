# Production

Production runs on the **ezgo host, `etopfunvalue.com`**, beside ezgo itself, at
**<https://giaky.etopfunvalue.com>**. It is built and pushed from the Jenkins host
(`192.168.1.100`) and deployed from there over SSH — the same shape as the ezgo pipelines.

| Piece | Where |
|-------|-------|
| nginx (host, TLS) | `giaky.etopfunvalue.com` → `127.0.0.1:8082` |
| Web (nginx container, serves the bundle and proxies the API on one origin) | `127.0.0.1:8082` |
| API (Spring Boot, profile `prod`) | private bridge network `giaky-prod`, not published at all |
| PostgreSQL | ezgo's DigitalOcean cluster, database `giaky`, user `doadmin` |
| Images | `registry.digitalocean.com/courier/giaky-api`, `.../giaky-web`, tag `build-<n>` |
| Uploaded media | Docker volume `giaky-prod-media` |
| Runtime config | `/etc/giaky/giaky.env` (0600), rewritten by every deploy |
| Compose file | `/etc/giaky/docker-compose.yml`, copied from this directory by every deploy |
| Swagger UI | off |

ezgo keeps `8081`; Gia Ký takes `8082`. Both are loopback-only, so neither sits in front of ufw.

Files: `deploy/prod/Jenkinsfile` (pipeline), `deploy/prod/docker-compose.yml` (what runs on the
host), `deploy/prod/nginx-giaky.conf` (the host vhost). The `giaky-prod` job itself is defined in
`deploy/jenkins/jobs.groovy` and created by the `giaky-seed` job (`deploy/jenkins/README.md`).

## One-time setup

### 1. The database

On ezgo's DigitalOcean cluster, create the database — only the database; the role is `doadmin`,
the same one ezgo uses:

```bash
doctl databases db create <cluster-id> giaky
```

Or from any psql connected to that cluster as `doadmin`: `CREATE DATABASE giaky;`.
Flyway creates the schema itself on the first deploy.

### 2. DNS

An `A` record for `giaky.etopfunvalue.com` pointing at the host, before certbot runs.

### 3. The host's nginx vhost

```bash
scp deploy/prod/nginx-giaky.conf root@etopfunvalue.com:/etc/nginx/sites-available/giaky
```

```bash
ssh root@etopfunvalue.com 'ln -sf /etc/nginx/sites-available/giaky /etc/nginx/sites-enabled/giaky && nginx -t && systemctl reload nginx && certbot --nginx -d giaky.etopfunvalue.com'
```

certbot rewrites the vhost in place to add the TLS listener and the redirect from `:80`.

### 4. Jenkins credentials

Three of the four already exist for ezgo and are reused as they are:

| ID | Kind | What |
|----|------|------|
| `do-registry-token` | Secret text | DigitalOcean token with registry write — **exists** |
| `ezvalue-ssh-key` | SSH private key | `root@etopfunvalue.com` — **exists** |
| `spring-datasource-password-do` | Secret text | the `doadmin` password — **exists**, shared with ezgo |
| `giaky-prod-jwt-secret` | Secret text | **add this one**: `openssl rand -base64 32` |
| `giaky-smtp` | Username with password | **add this one** (staging uses it too): see "Mail" below |

The JWT secret must be its own value, not the staging one: one leaked key should not sign tokens
for both environments. Rotating it signs everyone out.

### 5. The job

`giaky-prod` is created by the `giaky-seed` job from `deploy/jenkins/jobs.groovy`; set that up
once as `deploy/jenkins/README.md` describes. Nothing to create here by hand.

Two things on the host side are worth checking before the first run:

- **Docker Compose v2** (`docker compose version`). The Deploy stage checks first and tells you to
  `apt-get install -y docker-compose-plugin` if it is missing.
- **Registry capacity.** These are two new repositories (`giaky-api`, `giaky-web`) next to ezgo's
  one, and the API image is around 300 MB. DigitalOcean's Starter tier allows a single repository
  and 500 MB in all, so on Starter the Push stage fails until the registry is upgraded
  (`doctl registry get` shows the tier).

## Deploying

**Build with Parameters** on `giaky-prod`. There is no trigger: production moves when someone
decides it does. `giaky-staging` is the job that follows `main` on its own.

| Parameter | Effect |
|-----------|--------|
| `RUN_TESTS` (default on) | `./mvnw verify` with the Testcontainers ITs, then the web `verify` stage |
| `ROLLBACK_TAG` | e.g. `build-17`: redeploy that tag from the registry, skipping test, build and push |

Test API → Verify web → build both images → push to the registry → write `/etc/giaky/giaky.env`
and the compose file over SSH → `docker compose pull && up -d` → wait for `/actuator/health`
through the web container, then check the public URL. Flyway migrates `giaky` as the API starts.

## Rolling back

```bash
docker images registry.digitalocean.com/courier/giaky-api --format '{{.Tag}}'
```

Run `giaky-prod` with `ROLLBACK_TAG` set to the build you want. The three newest build tags stay
on the host; older ones are pulled back from the registry, which keeps them all.

A rollback redeploys **images only**. Flyway migrations are not reversed, so going back across a
schema change needs the matching database work by hand first. The compose file and the env file it
writes also still come from `main`, not from the commit the old image was built at — if the
deployment shape itself changed since, revert that commit instead of rolling the tag back.

## Operating it by hand

```bash
ssh root@etopfunvalue.com 'docker compose --env-file /etc/giaky/giaky.env -f /etc/giaky/docker-compose.yml ps'
```

```bash
ssh root@etopfunvalue.com 'docker compose --env-file /etc/giaky/giaky.env -f /etc/giaky/docker-compose.yml logs -f api'
```

`/etc/giaky/giaky.env` holds the database password, the JWT secret and the SMTP password. It is 0600 and owned by
root, and every deploy overwrites it from Jenkins' credential store — editing it by hand lasts
until the next build.

## Mail

Verification and password-reset emails are sent through a Gmail account over SMTP
(`smtp.gmail.com:587`, STARTTLS), by `SmtpVerificationTokenDelivery`. They are sent after the
account change commits, off the request thread; a failed send is logged as a `WARN` and never
fails the request, so the user just asks for another link.

**Setting up the Gmail account, once:**

1. Turn on 2-Step Verification for the account (Google Account → Security). App Passwords do not
   exist without it.
2. Google Account → Security → **App passwords** (or <https://myaccount.google.com/apppasswords>):
   create one named `giaky`. Google shows 16 letters in four groups; copy them **without the
   spaces**.
3. In Jenkins, add credential `giaky-smtp`, kind *Username with password*: username = the full
   Gmail address, password = the App Password. Both staging and production read it.

Emails go out from that address, shown as "Gia Ký". To show a different address, set `MAIL_FROM`
(Gmail only allows addresses added under *Send mail as* in that account). Gmail caps a personal
account at about 500 messages a day, and registration and forgot-password are rate-limited per IP,
which is plenty for a family tree. Revoking the App Password stops all mail at once.

**DigitalOcean and port 587.** DigitalOcean blocks outgoing SMTP on many accounts, and Gmail has no
other port to offer. The Smoke test checks from the host and prints a `WARNING` if
`smtp.gmail.com:587` cannot be reached. If it does, ask DigitalOcean support to lift the SMTP
block for the droplet. Until then the API runs normally and the emails are dropped with a `WARN`.

To check by hand: `ssh root@etopfunvalue.com 'timeout 5 bash -c "</dev/tcp/smtp.gmail.com/587" && echo open'`.
Each email sent or failed is logged by the API as `Sent a EMAIL_VERIFY email` or
`Sending a EMAIL_VERIFY email failed: …`.

## What is not set up

Nothing backs up `giaky-prod-media`. The database is covered by DigitalOcean's cluster backups,
the same ones ezgo relies on; the uploaded media volume is not.
