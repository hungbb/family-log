# syntax=docker/dockerfile:1.7
#
# Gia Ký web client — static bundle served by nginx, which also reverse-proxies the API so the
# browser sees one origin (src/api/client.ts: an unset VITE_API_BASE_URL means "same origin").
#
# Targets:
#   verify  — lint, constraint fixtures, typecheck, unit tests, build (what `npm run verify` does)
#   runtime — the image that is deployed (default)

# --- Dependencies -----------------------------------------------------------------------------
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
# --ignore-scripts: `prepare` installs husky into the parent git repo, which does not exist here.
RUN --mount=type=cache,target=/root/.npm npm ci --ignore-scripts

FROM deps AS source
COPY . .

# --- Verify (CI gate) -------------------------------------------------------------------------
# src/api/devDefaults.test.ts reads the repo-root .gitignore, which is outside this build context:
#   docker build --target verify --build-context repo-root=. web
FROM source AS verify
COPY --from=repo-root .gitignore /.gitignore
RUN npm run verify

# --- Build ------------------------------------------------------------------------------------
FROM source AS build
# Empty on purpose: same-origin, behind the nginx proxy below. Override only for a split-origin
# deployment (and then the API's CORS_ALLOWED_ORIGINS must list this site).
ARG VITE_API_BASE_URL=
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
RUN npm run build

# --- Runtime ----------------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Rendered by the nginx image's entrypoint (envsubst) at container start.
ENV API_UPSTREAM=http://127.0.0.1:8180 \
    LISTEN_PORT=80
COPY deploy/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
