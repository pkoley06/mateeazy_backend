# MateEazy Staging Setup Runbook

Production gets a **fresh, empty database and photo bucket**; all the
closed-testing data lives on a separate **staging** backend at
`https://staging-api.mateeazy.in`.

Both backends run on the same OCI VM, from the same Docker image, against the
same PostgreSQL server — but each has its own database, env file, port,
JWT secret and photo bucket, so nothing crosses between them.

```
                           +----------------------------------------------------------+
                           |                         OCI VM                           |
  api.mateeazy.in    ----> | nginx :443 --> 127.0.0.1:8080  matchmate-backend         |
  (Play production app)    |                     +--> PostgreSQL: mateeazy_db  (fresh) |
                           |                     +--> bucket: mateeazy-bucket-prod     |
                           |                                                          |
  staging-api.mateeazy.in  | nginx :443 --> 127.0.0.1:8081  mateeazy-backend-staging  |
  (dev-test builds)  ----> |                     +--> PostgreSQL: mateeazy_staging     |
                           |                     +--> bucket: matchmate-bucket         |
                           +----------------------------------------------------------+
```

| | Production | Staging |
|---|---|---|
| Domain | `api.mateeazy.in` | `staging-api.mateeazy.in` |
| Port (loopback) | `8080` | `8081` |
| Database | `mateeazy_db` (fresh, created in Part 2) | `mateeazy_staging` (copy of the closed-testing data) |
| Photo bucket | `mateeazy-bucket-prod` (Standard, public, no listing) | `matchmate-bucket` (testers' photos) |
| Env file | `/etc/matchmate-backend.env` | `/etc/mateeazy-backend-staging.env` |
| Compose dir | `/opt/matchmate-backend` | `/opt/mateeazy-backend-staging` |
| Container / journald tag | `matchmate-backend` | `mateeazy-backend-staging` |
| nginx site | `matchmate-backend` | `mateeazy-backend-staging` |
| Deploys | GitHub Actions (unchanged) | manual (Part 3) |
| Flutter env | `env/prod.json` | `env/dev-test.json` |
| Admin panel env | `env/oci.json` | `env/staging.json` |

The old closed-testing database `matchmate_db` is kept untouched after the
switch, as an archive (drop it once you no longer need it — Part 4).

Names of the *server-side* pieces created for this split use **mateeazy**.
The production container, image (`ghcr.io/pkoley06/matchmate-backend`), deploy
script and GitHub workflows keep their `matchmate` names — they are wired into
CI and nobody using the app ever sees them.

---

## Status

| Part | What | Status |
|---|---|---|
| 1 | Staging backend, DNS, HTTPS, app env files | **Done 2026-11-18** |
| 2 | Switch production to the fresh database + bucket | **To do — after Google approves production access, right before rollout** |
| 3 | Day-to-day staging operations | reference |
| 4 | Cleanup | later |

---

## Part 1: Staging backend (done 2026-11-18 — kept for rebuilding)

### 1. Backup of the closed-testing database

```bash
sudo mkdir -p /var/backups/postgresql
sudo chown postgres:postgres /var/backups/postgresql
sudo chmod 700 /var/backups/postgresql
sudo -u postgres pg_dump -Fc matchmate_db \
  -f /var/backups/postgresql/matchmate_db-before-split.dump
sudo chmod 600 /var/backups/postgresql/matchmate_db-before-split.dump
sudo -u postgres pg_restore -l /var/backups/postgresql/matchmate_db-before-split.dump | head
```

An off-server copy is kept on the dev PC (`D:\Projects\personal\matchmate\backups\`,
outside every git repo — it contains testers' personal data):
```powershell
scp pkoci:~/matchmate_db-before-split.dump D:\Projects\personal\matchmate\backups\
```

### 2. Staging database = copy of that backup

```bash
sudo -u postgres psql -c "CREATE DATABASE mateeazy_staging OWNER dev_user;"
sudo -u postgres pg_restore -d mateeazy_staging /var/backups/postgresql/matchmate_db-before-split.dump
sudo -u postgres psql -d mateeazy_staging -c "SELECT count(*) AS users FROM app_users;"   # 57 at copy time
```

### 3. Staging env file

```bash
sudo cp /etc/matchmate-backend.env /etc/mateeazy-backend-staging.env
sudo chown root:root /etc/mateeazy-backend-staging.env
sudo chmod 600 /etc/mateeazy-backend-staging.env

sudo sed -i \
  -e 's|^SERVER_PORT=.*|SERVER_PORT=8081|' \
  -e 's|^DB_NAME=.*|DB_NAME=mateeazy_staging|' \
  -e 's|^DB_URL=.*|DB_URL=jdbc:postgresql://127.0.0.1:5432/mateeazy_staging|' \
  -e 's|^JAVA_OPTS=.*|JAVA_OPTS=-Xms128m -Xmx384m -XX:+UseG1GC|' \
  /etc/mateeazy-backend-staging.env

# Its own JWT secret, so a staging login never works on production
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" /etc/mateeazy-backend-staging.env
```

`STORAGE_*` stays as copied — `matchmate-bucket` is the staging bucket.
`FIREBASE_CREDENTIALS_PATH` stays as copied — both backends share the Firebase
project (sign-in and push work for both).

Check (prints no secrets):
```bash
sudo grep -E '^(SERVER_ADDRESS|SERVER_PORT|SPRING_PROFILES_ACTIVE|DB_NAME|DB_URL|JAVA_OPTS|STORAGE_PROVIDER|STORAGE_BUCKET|FIREBASE_CREDENTIALS_PATH)=' /etc/mateeazy-backend-staging.env
[ "$(sudo grep '^JWT_SECRET=' /etc/matchmate-backend.env)" != "$(sudo grep '^JWT_SECRET=' /etc/mateeazy-backend-staging.env)" ] && echo different || echo SAME
```

> [!CAUTION]
> Type the comparison line exactly, including the leading `[`. Without it the
> shell tries to *run* the first secret and prints it on screen.

### 4. Staging container

```bash
sudo mkdir -p /opt/mateeazy-backend-staging
sudo tee /opt/mateeazy-backend-staging/compose.yml > /dev/null << 'EOF'
name: mateeazy-backend-staging

services:
  app:
    image: ghcr.io/pkoley06/matchmate-backend:${IMAGE_TAG:?}
    container_name: mateeazy-backend-staging
    restart: unless-stopped
    network_mode: host
    env_file:
      - /etc/mateeazy-backend-staging.env
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=128m
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    volumes:
      - /etc/matchmate-backend:/etc/matchmate-backend:ro
    logging:
      driver: journald
      options:
        tag: mateeazy-backend-staging
EOF
sudo chown -R root:root /opt/mateeazy-backend-staging
sudo chmod 644 /opt/mateeazy-backend-staging/compose.yml

# Same image production runs
sudo cp /opt/matchmate-backend/.env /opt/mateeazy-backend-staging/.env
sudo chmod 600 /opt/mateeazy-backend-staging/.env

cd /opt/mateeazy-backend-staging && sudo docker compose up -d app
sleep 40
curl -s http://127.0.0.1:8081/health; echo
```

### 5. DNS (Cloudflare, zone `mateeazy.in`)

| Type | Name | IPv4 address | Proxy status |
|---|---|---|---|
| `A` | `staging-api` | `130.210.45.27` | **DNS only** (grey cloud) |

### 6. nginx + HTTPS

Port `8081` stays on loopback — **never open it in the OCI Security List.**

```bash
sudo tee /etc/nginx/sites-available/mateeazy-backend-staging > /dev/null << 'EOF'
# The $connection_upgrade map is defined in the production site
# (matchmate-backend); nginx shares it across all sites.
server {
    listen 80;
    listen [::]:80;
    server_name staging-api.mateeazy.in;

    client_max_body_size 16M;

    location / {
        proxy_pass http://127.0.0.1:8081;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # Required for the /ws-comments WebSocket (live comments/chat)
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
    }
}
EOF
sudo ln -sf /etc/nginx/sites-available/mateeazy-backend-staging /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

sudo certbot --nginx -d staging-api.mateeazy.in --redirect \
  -m support@mateeazy.in --agree-tos --no-eff-email

curl -s https://staging-api.mateeazy.in/health; echo    # {"status":"UP"}
```

### 7. Apps

- `matchmate_flutter/env/dev-test.json` → `"API_BASE_URL": "https://staging-api.mateeazy.in"`
  (VS Code launch config **"matchmate_flutter (dev-test)"** uses it).
  `env/prod.json` stays on `https://api.mateeazy.in`.
- `matchmate_admin/env/staging.json` → same URL; run the admin panel with
  `--dart-define-from-file=env/staging.json` to manage staging. Its admin
  login is the one from the closed-testing database.

> [!WARNING]
> Never upload a staging build to **any** Google Play track. All tracks share
> one package name and a user gets the highest version code available to them,
> so a staging build could replace the production app on their phone. Staging
> builds are side-loaded only (or Firebase App Distribution).

Staging has its own JWT secret: sign in once on a staging build.

---

## Part 2: Switch production to the fresh database (at launch)

**When:** after Google has approved production access, with the production
release prepared but **not yet rolled out**. Tell testers first — from this
moment the live app starts empty and everyone signs up again (their Firebase
accounts still exist, so sign-in works; they just get a new profile and
onboarding).

Production keeps running its env file — only `DB_NAME`/`DB_URL`, the JWT secret
and the bucket change. The old `matchmate_db` is not touched, so undoing is just
restoring the old env lines.

### 1. Fresh backup (captures anything added since Part 1)

```bash
sudo -u postgres pg_dump -Fc matchmate_db \
  -f /var/backups/postgresql/matchmate_db-at-launch.dump
sudo chmod 600 /var/backups/postgresql/matchmate_db-at-launch.dump
sudo ls -lh /var/backups/postgresql/
```

Optional — refresh staging with it, so staging has the very latest testing data:
```bash
cd /opt/mateeazy-backend-staging && sudo docker compose stop app
sudo -u postgres psql -c "DROP DATABASE mateeazy_staging;"
sudo -u postgres psql -c "CREATE DATABASE mateeazy_staging OWNER dev_user;"
sudo -u postgres pg_restore -d mateeazy_staging /var/backups/postgresql/matchmate_db-at-launch.dump
sudo docker compose start app
```

### 2. Keep a copy of the current production env

```bash
sudo cp -p /etc/matchmate-backend.env /etc/matchmate-backend.env.before-launch
```

### 3. Create the fresh production database

```bash
sudo -u postgres psql -c "CREATE DATABASE mateeazy_db OWNER dev_user;"
sudo -u postgres psql -d mateeazy_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### 4. Point production at it — new database, new bucket, new JWT secret

The JWT secret is rotated because the old one was once printed on screen
(2026-11-18). Rotating it signs everyone out — which happens at the switch anyway.

```bash
sudo sed -i \
  -e 's|^DB_NAME=.*|DB_NAME=mateeazy_db|' \
  -e 's|^DB_URL=.*|DB_URL=jdbc:postgresql://127.0.0.1:5432/mateeazy_db|' \
  -e 's|^STORAGE_BUCKET=.*|STORAGE_BUCKET=mateeazy-bucket-prod|' \
  -e 's|^STORAGE_PUBLIC_BASE_URL=.*|STORAGE_PUBLIC_BASE_URL=https://objectstorage.ap-mumbai-1.oraclecloud.com/n/bmlk46grfoot/b/mateeazy-bucket-prod/o|' \
  /etc/matchmate-backend.env
sudo sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 32)|" /etc/matchmate-backend.env

sudo grep -E '^(DB_NAME|DB_URL|STORAGE_BUCKET|STORAGE_PUBLIC_BASE_URL)=' /etc/matchmate-backend.env
```

### 5. Restart production — Flyway builds the schema

`restart` does not re-read the env file; recreate the container instead:

```bash
cd /opt/matchmate-backend
sudo docker compose up -d --force-recreate app
sleep 40
sudo journalctl -t matchmate-backend -n 120 --no-pager | grep -iE "flyway|migrat|S3StorageService|Started|ERROR"
curl -fsS http://127.0.0.1:8080/health; echo
```

Expect: Flyway applies all migrations to `mateeazy_db`,
`S3StorageService initialized (bucket=mateeazy-bucket-prod …)`, and
`{"status":"UP"}`. The admin login is re-created from `ADMIN_SEED_USERNAME` /
`ADMIN_SEED_PASSWORD` because the admin table is empty.

### 6. Copy the branding settings across

A fresh database only has the default branding row (no logo, community on, no
Maps key, `latestBuildNumber` unset):

```bash
sudo -u postgres pg_dump --data-only --table=app_branding matchmate_db > /tmp/branding.sql
sudo -u postgres psql -d mateeazy_db -v ON_ERROR_STOP=1 -c "TRUNCATE app_branding;" -f /tmp/branding.sql
rm /tmp/branding.sql

cd /opt/matchmate-backend && sudo docker compose restart app
```

> [!NOTE]
> Branding images (logo, splash) uploaded through the admin panel point at
> `matchmate-bucket`. They keep loading — that bucket stays public — but if you
> want production fully independent of the staging bucket, re-upload them in the
> admin panel after the switch.

### 7. Verify

```bash
sudo -u postgres psql -d mateeazy_db -c "SELECT count(*) AS users FROM app_users;"   # 0
curl -s https://api.mateeazy.in/api/v1/app/version; echo
curl -s https://staging-api.mateeazy.in/health; echo                               # staging unaffected
```

- Admin panel (production, `env/oci.json`): log in with the seed admin and check
  **Branding** — logo, community **off**, Maps key, `latestBuildNumber`.
- Install the production build, sign up with a new account, upload a photo —
  its URL must contain `mateeazy-bucket-prod` and load in a browser.
- Then roll out the production release in Play Console.

### Undo Part 2

```bash
sudo cp -p /etc/matchmate-backend.env.before-launch /etc/matchmate-backend.env
cd /opt/matchmate-backend && sudo docker compose up -d --force-recreate app
# optional, to retry from scratch later:
sudo -u postgres psql -c "DROP DATABASE mateeazy_db;"
```

### After the switch

- Point the nightly backup (`DEPLOY-RUNBOOK.md` Step F) at **`mateeazy_db`**
  (`DB_NAME="mateeazy_db"` in the script) — it was written for `matchmate_db`.
- Delete `/etc/matchmate-backend.env.before-launch` once production has run
  fine for a few days (it holds the old secrets).

---

## Part 3: Day-to-day staging

### Update staging to a newer backend build

GitHub Actions deploys **production only**. Staging is switched by hand to any
image tag (commit SHA) present on the VM:

```bash
sudo docker images ghcr.io/pkoley06/matchmate-backend --format "{{.Tag}}  {{.CreatedSince}}"

echo "IMAGE_TAG=<40-char sha>" | sudo tee /opt/mateeazy-backend-staging/.env > /dev/null
cd /opt/mateeazy-backend-staging && sudo docker compose up -d app
curl -fsS http://127.0.0.1:8081/health; echo
```

To try a build on staging **before** production gets it, pull it first (GitHub
token with `read:packages`):
```bash
echo "<token>" | sudo docker login ghcr.io -u pkoley06 --password-stdin
sudo docker pull ghcr.io/pkoley06/matchmate-backend:<sha>
sudo docker logout ghcr.io
```

> [!CAUTION]
> Flyway migrations only go forward. Once a newer image has migrated staging,
> staging cannot go back to an older image.

### Logs, restart, stop

```bash
sudo journalctl -t mateeazy-backend-staging -f
cd /opt/mateeazy-backend-staging && sudo docker compose restart app
cd /opt/mateeazy-backend-staging && sudo docker compose stop app   # frees its memory
```

Env-file changes need `sudo docker compose up -d --force-recreate app`, not `restart`.

### Backups

If the staging data matters, back it up nightly too (after Step F is installed):
```bash
sudo sed 's/DB_NAME="[^"]*"/DB_NAME="mateeazy_staging"/' \
  /usr/local/bin/backup-matchmate-db.sh | sudo tee /usr/local/bin/backup-mateeazy-staging-db.sh > /dev/null
sudo chmod 755 /usr/local/bin/backup-mateeazy-staging-db.sh
sudo crontab -u postgres -l | { cat; echo "30 2 * * * /usr/local/bin/backup-mateeazy-staging-db.sh >> /var/log/postgresql/backup-staging.log 2>&1"; } | sudo crontab -u postgres -
```

---

## Part 4: Cleanup (later)

Drop the archived closed-testing database once you are sure you no longer need
it (staging has its own copy, and the dumps exist):
```bash
sudo -u postgres psql -c "DROP DATABASE matchmate_db;"   # irreversible
```

Remove staging entirely:
```bash
cd /opt/mateeazy-backend-staging && sudo docker compose down
sudo rm /etc/nginx/sites-enabled/mateeazy-backend-staging && sudo systemctl reload nginx
sudo certbot delete --cert-name staging-api.mateeazy.in
sudo -u postgres psql -c "DROP DATABASE mateeazy_staging;"   # irreversible
sudo rm /etc/mateeazy-backend-staging.env
# + delete the Cloudflare DNS record `staging-api`
```

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| Production won't start after Part 2, Flyway error about `postgis` | Extension not created | Part 2 step 3, second command |
| `permission denied for schema public` | New DB/schema not owned by the app user | as postgres: `ALTER DATABASE mateeazy_db OWNER TO dev_user;` and in `mateeazy_db`: `ALTER SCHEMA public OWNER TO dev_user;` |
| Production still shows the old data after Part 2 | Container restarted, not recreated — old env still loaded | `sudo docker compose up -d --force-recreate app` |
| App shows the default logo / community on | Branding not copied | Part 2 step 6 |
| New photos fail to upload in production | Bucket name/URL typo, or bucket not in the same compartment as the keys | check `STORAGE_*`, OCI bucket details |
| Photos upload but don't display | Bucket still **Private**, or created in **Archive** tier | Bucket → Policies → Edit Visibility → Public; Archive tier can't be changed — recreate as Standard |
| `502 Bad Gateway` on staging domain | Staging container down | `sudo docker ps`, then the logs command |
| Certbot fails | DNS not propagated, or Cloudflare proxy (orange cloud) on | wait / switch to **DNS only** |
| VM sluggish | Two JVMs + PostgreSQL | lower staging `-Xmx`, or stop staging when not testing |
