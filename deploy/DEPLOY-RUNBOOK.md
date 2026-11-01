# MatchMate Backend Production Deployment Runbook

This runbook contains the complete, one-time setup instructions for deploying `matchmate-backend` to an Oracle Cloud Infrastructure (OCI) Ampere A1 (`linux/arm64`) Virtual Machine.

---

## Architecture Overview

```
                      +------------------------------------------+
                      |                 OCI VM                   |
                      |                                          |
  GitHub Actions ---> | :22   deploy@<OCI_HOST>                  |
  (Deploy Job)        |       |                                  |
                      |       +--> sudo matchmate-backend-deploy |
                      |                                          |
  Client Traffic ---> | :80   Nginx Reverse Proxy                |
  (HTTP / App)        |       |                                  |
                      |       +--> 127.0.0.1:8080 (Docker Host)  |
                      |                 |                        |
                      |                 v                        |
                      |        matchmate-backend Container       |
                      |        (ghcr.io/pkoley06/matchmate-backend)|
                      |                 |                        |
                      |                 +--> 127.0.0.1:5432       |
                      |                      PostgreSQL + PostGIS|
                      +------------------------------------------+
```

* **App Slug**: `matchmate-backend`
* **GitHub Repository**: `pkoley06/matchmate_backend`
* **Image Registry**: `ghcr.io/pkoley06/matchmate-backend`
* **Architecture**: `linux/arm64` (OCI Ampere A1)
* **SSH Host String**: `pkoci` (local config) / Public IP in CI secret `OCI_HOST`
* **Port**: `8080` (bound to `127.0.0.1`, proxied via Nginx)

---

## Step A: Enable GitHub Actions & Push Workflows

GitHub Actions is serverless and built directly into GitHub. You do **not** need to install any agents, plugins, or third-party servers. Placing workflow files inside the `.github/workflows/` directory and pushing them to your repository is what automatically activates GitHub Actions.

### 1. Verify GitHub Actions Permissions (One-Time Setting)
Before pushing, ensure your GitHub repository allows Actions and has permission to publish container images:
1. Navigate to: **`https://github.com/pkoley06/matchmate_backend/settings/actions`** (Settings -> Actions -> General).
2. Under **Actions permissions**:
   - Select **Allow all actions and reusable workflows**.
3. Under **Workflow permissions**:
   - Select **Read and write permissions** (this allows the `GITHUB_TOKEN` to push images to GitHub Container Registry `ghcr.io` and delete old image versions).
   - Check **Allow GitHub Actions to create and approve pull requests** (optional, recommended).
4. Click **Save**.

### 2. Commit and Push the Workflow Files
The repository includes two workflows:
* [`.github/workflows/ci-deploy.yml`](../.github/workflows/ci-deploy.yml): Triggers automatically on push to `main` and `dev`, builds the jar, builds the ARM64 Docker image, pushes to GHCR, and invokes deployment.
* [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml): Reusable and manual dispatch workflow for deployments and rollbacks.

Stage and commit all generated files from your local repository (on `dev` or `main`):
```bash
git add .github/ Dockerfile .dockerignore deploy/ src/
git commit -m "feat(deploy): setup CI/CD pipeline, dockerization, and deploy runbook"
git push origin dev   # or git push origin main
```

> [!NOTE]
> Once pushed, open the **Actions** tab in GitHub (`https://github.com/pkoley06/matchmate_backend/actions`). You will see the **CI & Deploy** workflow appear immediately!
> The `ci` and `image` jobs will succeed and publish your container image to GHCR.
> The final `deploy` job will wait or fail on this very first run until you finish creating the SSH key and GitHub `production` environment secrets in the following steps. This is completely normal and expected.

---

## Step B: Generate Dedicated Deploy SSH Key

Generate an isolated ED25519 SSH keypair on your local machine specifically for CI deployments:

```bash
# 1. Generate keypair without passphrase
ssh-keygen -t ed25519 -C "github-deploy@matchmate-backend" -f ./id_ed25519_deploy -N ""

# 2. Extract your VM host key into known_hosts format (replace <VM_IP> with your OCI VM Public IP)
ssh-keyscan -H <VM_IP> > known_hosts_entry
ssh-keygen -F <VM_IP> -f known_hosts_entry
```

Keep `./id_ed25519_deploy` (private key) and `./known_hosts_entry` open for Step G.

---

## Step C: VM Setup

Log into your OCI VM via SSH:
```bash
ssh pkoci
```

### 1. Install Docker and Compose Plugin
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg zstd

sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
```

### 2. Verify PostgreSQL and PostGIS Loopback Login
Ensure PostgreSQL and PostGIS are installed and running locally:
```bash
sudo apt-get install -y postgresql postgresql-contrib postgresql-16-postgis-3
sudo systemctl enable --now postgresql
```

Verify connection and PostGIS extension over loopback using your existing credentials:
```bash
PGPASSWORD='matchmate' psql -h 127.0.0.1 -U dev_user -d matchmate_db -c "SELECT postgis_full_version();"
```

If the PostGIS extension was not created yet on `matchmate_db`, enable it:
```bash
sudo -u postgres psql -d matchmate_db -c "CREATE EXTENSION IF NOT EXISTS postgis;"
```

### 3. Create Secrets and Environment Directory
```bash
sudo mkdir -p /etc/matchmate-backend
sudo chmod 700 /etc/matchmate-backend
```

Create `/etc/matchmate-backend.env`:
```bash
sudo tee /etc/matchmate-backend.env > /dev/null << 'EOF'
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
SERVER_FORWARD_HEADERS_STRATEGY=framework
JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC

DB_NAME=matchmate_db
DB_USER=dev_user
DB_PASSWORD=matchmate
DB_URL=jdbc:postgresql://127.0.0.1:5432/matchmate_db

JWT_SECRET=GENERATE_WITH_OPENSSL_RAND_BASE64_32
FIREBASE_CREDENTIALS_PATH=file:/etc/matchmate-backend/firebase-service-account.json

ADMIN_SEED_USERNAME=admin
ADMIN_SEED_PASSWORD=YOUR_STRONG_ADMIN_PASSWORD
EOF

# Lock down file permissions to root only
sudo chown root:root /etc/matchmate-backend.env
sudo chmod 600 /etc/matchmate-backend.env
```

Generate a secure 32-byte Base64 JWT secret and insert it:
```bash
JWT_KEY=$(openssl rand -base64 32)
sudo sed -i "s|JWT_SECRET=.*|JWT_SECRET=${JWT_KEY}|" /etc/matchmate-backend.env
```

Place your Firebase service account JSON file at `/etc/matchmate-backend/firebase-service-account.json`:
```bash
# Upload or paste your firebase-service-account.json
sudo nano /etc/matchmate-backend/firebase-service-account.json
sudo chown root:root /etc/matchmate-backend/firebase-service-account.json
sudo chmod 600 /etc/matchmate-backend/firebase-service-account.json
```

### 4. Create Dedicated `deploy` User
```bash
# Create user without shell login password and NOT in docker group
sudo useradd -m -s /bin/bash deploy
sudo mkdir -p /home/deploy/.ssh
sudo chmod 700 /home/deploy/.ssh

# Authorize the deploy key generated in Step B with strict restrictions
sudo tee /home/deploy/.ssh/authorized_keys > /dev/null << 'EOF'
restrict,pty,command="sudo /usr/local/bin/matchmate-backend-deploy *" ssh-ed25519 YOUR_PUBLIC_KEY_CONTENT_FROM_id_ed25519_deploy.pub github-deploy@matchmate-backend
EOF

sudo chmod 600 /home/deploy/.ssh/authorized_keys
sudo chown -R deploy:deploy /home/deploy/.ssh

# Verify deploy is NOT in docker group (returns only deploy)
groups deploy
```

### 5. Install Script and Compose File
```bash
# 1. Compose application directory
sudo mkdir -p /opt/matchmate-backend
sudo tee /opt/matchmate-backend/compose.yml > /dev/null << 'EOF'
name: matchmate-backend-app

services:
  app:
    image: ghcr.io/pkoley06/matchmate-backend:${IMAGE_TAG:?}
    container_name: matchmate-backend
    restart: unless-stopped
    network_mode: host
    env_file:
      - /etc/matchmate-backend.env
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
        tag: matchmate-backend
EOF

sudo chown -R root:root /opt/matchmate-backend
sudo chmod 755 /opt/matchmate-backend
sudo chmod 644 /opt/matchmate-backend/compose.yml

# 2. Copy the deploy script from the repo to /usr/local/bin
sudo tee /usr/local/bin/matchmate-backend-deploy > /dev/null << 'EOF'
#!/usr/bin/env bash
set -euo pipefail

TARGET_SHA="${1:-}"
if [[ ! "${TARGET_SHA}" =~ ^[0-9a-f]{40}$ ]]; then
    echo "Error: Commit SHA '${TARGET_SHA}' must be a full 40-character hexadecimal string." >&2
    exit 1
fi

SLUG="matchmate-backend"
OWNER="pkoley06"
IMAGE_NAME="ghcr.io/${OWNER}/${SLUG}"
APP_DIR="/opt/${SLUG}"
COMPOSE_FILE="${APP_DIR}/compose.yml"
ENV_FILE="${APP_DIR}/.env"
HEALTH_URL="http://127.0.0.1:8080/health"
TIMEOUT=120

GHCR_TOKEN=""
if [ ! -t 0 ]; then
    read -r GHCR_TOKEN || true
fi

if [ -z "${GHCR_TOKEN}" ]; then
    echo "Error: No GHCR token provided over stdin." >&2
    exit 1
fi

TMP_DOCKER_DIR="$(mktemp -d /tmp/docker-cfg.XXXXXX)"
cleanup() {
    rm -rf "${TMP_DOCKER_DIR}"
}
trap cleanup EXIT

export DOCKER_CONFIG="${TMP_DOCKER_DIR}"
echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${OWNER}" --password-stdin >/dev/null 2>&1

PREV_TAG=""
if [ -f "${ENV_FILE}" ]; then
    PREV_TAG=$(grep -E '^IMAGE_TAG=' "${ENV_FILE}" | cut -d'=' -f2- | tr -d ' "')
fi

if [ "${PREV_TAG}" = "${TARGET_SHA}" ]; then
    echo "Image tag ${TARGET_SHA} is currently active in ${ENV_FILE}. Verifying health..."
    if docker compose -f "${COMPOSE_FILE}" ps --status running | grep -q "${SLUG}"; then
        if curl -fsS -o /dev/null -m 5 "${HEALTH_URL}" 2>/dev/null; then
            echo "Container is already running ${TARGET_SHA} and healthy. Skipping deployment."
            exit 0
        fi
    fi
fi

echo "Pulling image ${IMAGE_NAME}:${TARGET_SHA}..."
docker pull "${IMAGE_NAME}:${TARGET_SHA}"

rollback() {
    echo "==========================================================" >&2
    echo "DEPLOYMENT FAILED! Outputting last 80 container log lines:" >&2
    docker compose -f "${COMPOSE_FILE}" logs --tail=80 app >&2 || true
    echo "==========================================================" >&2

    if [ -n "${PREV_TAG}" ] && [ "${PREV_TAG}" != "${TARGET_SHA}" ]; then
        echo "Rolling back to previous tag: ${PREV_TAG}..." >&2
        TMP_ENV="${ENV_FILE}.tmp.$$"
        echo "IMAGE_TAG=${PREV_TAG}" > "${TMP_ENV}"
        chmod 600 "${TMP_ENV}"
        mv -f "${TMP_ENV}" "${ENV_FILE}"
        docker compose -f "${COMPOSE_FILE}" up -d app
        echo "Rollback to ${PREV_TAG} completed." >&2
    else
        echo "No previous tag found to roll back to." >&2
    fi
    exit 1
}

mkdir -p "${APP_DIR}"
TMP_ENV="${ENV_FILE}.tmp.$$"
echo "IMAGE_TAG=${TARGET_SHA}" > "${TMP_ENV}"
chmod 600 "${TMP_ENV}"
mv -f "${TMP_ENV}" "${ENV_FILE}"

echo "Starting container with image tag ${TARGET_SHA}..."
docker compose -f "${COMPOSE_FILE}" up -d app

echo "Waiting up to ${TIMEOUT}s for container to become healthy at ${HEALTH_URL}..."
START_TIME=$(date +%s)
HEALTHY=0

while [ $(($(date +%s) - START_TIME)) -lt ${TIMEOUT} ]; do
    CONTAINER_RUNNING=0
    HEALTH_OK=0

    if docker compose -f "${COMPOSE_FILE}" ps --status running | grep -q "${SLUG}"; then
        CONTAINER_RUNNING=1
    fi

    if curl -fsS -o /dev/null -m 2 "${HEALTH_URL}" 2>/dev/null; then
        HEALTH_OK=1
    fi

    if [ "${CONTAINER_RUNNING}" -eq 1 ] && [ "${HEALTH_OK}" -eq 1 ]; then
        HEALTHY=1
        break
    fi

    sleep 3
done

if [ "${HEALTHY}" -ne 1 ]; then
    rollback
fi

echo "Health check succeeded: container is running and healthy!"

echo "Pruning older local images (keeping 5 newest)..."
IMAGE_IDS=$(docker images "${IMAGE_NAME}" --format "{{.ID}}" | awk '!seen[$0]++')
TOTAL_IMAGES=$(echo "${IMAGE_IDS}" | grep -c . || true)

if [ "${TOTAL_IMAGES}" -gt 5 ]; then
    OLD_IMAGES=$(echo "${IMAGE_IDS}" | tail -n +"$((5 + 1))")
    for img in ${OLD_IMAGES}; do
        docker rmi -f "${img}" 2>/dev/null || true
    done
fi
docker image prune -f 2>/dev/null || true

echo "Deployment of ${TARGET_SHA} finished successfully!"
EOF

sudo chown root:root /usr/local/bin/matchmate-backend-deploy
sudo chmod 755 /usr/local/bin/matchmate-backend-deploy
```

### 6. Sudoers Configuration
Grant the `deploy` user permission to run ONLY this specific script:
```bash
sudo tee /etc/sudoers.d/matchmate-backend-deploy > /dev/null << 'EOF'
deploy ALL=(ALL) NOPASSWD: /usr/local/bin/matchmate-backend-deploy
EOF

sudo chmod 440 /etc/sudoers.d/matchmate-backend-deploy
# Verify sudoers syntax
sudo visudo -cf /etc/sudoers.d/matchmate-backend-deploy
sudo visudo -c
```

### 7. Enforce SSH Password Authentication Disabled
Check that password authentication is turned off:
```bash
sudo grep -E "^(PasswordAuthentication|KbdInteractiveAuthentication)" /etc/ssh/sshd_config /etc/ssh/sshd_config.d/*.conf 2>/dev/null || true
```
Ensure both are set to `no`.

---

## Step D: Network Configuration

1. **OCI Cloud Console (VCN Security List)**:
   - Navigate to: **Networking -> Virtual Cloud Networks -> Your VCN -> Security Lists -> Default Security List**.
   - **Ingress Rules**:
     - Port 22 (SSH): CIDR `0.0.0.0/0`
     - Port 80 (HTTP): CIDR `0.0.0.0/0`
     - Port 443 (HTTPS): CIDR `0.0.0.0/0`
   - **CRITICAL**: Never open port `8080` (Spring Boot) or `5432` (PostgreSQL) in OCI Ingress Rules.

2. **VM Host Firewall (iptables)**:
   Ubuntu images on Oracle Cloud enforce default iptables rules that drop ports 80/443 even if security lists allow them. Open them on the VM:
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save || sudo apt-get install -y iptables-persistent && sudo netfilter-persistent save
   ```

---

## Step E: Nginx Reverse Proxy Setup (Direct IP Mode)

Since there is currently no domain name assigned, configure Nginx to proxy port 80 directly to `127.0.0.1:8080`:

```bash
sudo apt-get install -y nginx

sudo tee /etc/nginx/sites-available/matchmate-backend > /dev/null << 'EOF'
# Without this map, nginx does not forward the client's "Upgrade" header to
# the upstream at all (its documented default for WebSocket proxying), so
# Spring's handshake handler sees no Upgrade header and rejects every
# /ws-comments connection with "Can 'Upgrade' only to 'WebSocket'.".
map $http_upgrade $connection_upgrade {
    default upgrade;
    ''      close;
}

server {
    listen 80 default_server;
    listen [::]:80 default_server;
    server_name _;

    client_max_body_size 16M;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        # Required for the /ws-comments STOMP WebSocket handshake to upgrade.
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection $connection_upgrade;
    }
}
EOF

sudo rm -f /etc/nginx/sites-enabled/default
sudo ln -sf /etc/nginx/sites-available/matchmate-backend /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl restart nginx
```

> [!NOTE]
> When you assign a domain in the future, update `server_name yourdomain.com;` in `/etc/nginx/sites-available/matchmate-backend` and obtain a free SSL certificate with:
> ```bash
> sudo apt-get install -y certbot python3-certbot-nginx
> sudo certbot --nginx -d yourdomain.com
> ```

---

## Step F: Backup Cron & Restore Verification

1. Install the backup script on the VM:
   ```bash
   sudo tee /usr/local/bin/backup-matchmate-db.sh > /dev/null << 'EOF'
   #!/usr/bin/env bash
   set -euo pipefail

   DB_NAME="matchmate_db"
   BACKUP_BASE="/var/backups/postgresql"
   DAILY_DIR="${BACKUP_BASE}/daily"
   WEEKLY_DIR="${BACKUP_BASE}/weekly"
   TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
   DAY_OF_WEEK=$(date +"%u")

   mkdir -p "${DAILY_DIR}" "${WEEKLY_DIR}"
   chmod 700 "${BACKUP_BASE}" "${DAILY_DIR}" "${WEEKLY_DIR}"

   DAILY_FILE="${DAILY_DIR}/${DB_NAME}-daily-${TIMESTAMP}.dump.zst"
   echo "[$(date -Iseconds)] Starting backup for database '${DB_NAME}'..."
   pg_dump -Fc "${DB_NAME}" | zstd -q -3 -o "${DAILY_FILE}"
   chmod 600 "${DAILY_FILE}"
   echo "[$(date -Iseconds)] Daily backup created: ${DAILY_FILE} ($(du -h "${DAILY_FILE}" | cut -f1))"

   if [ "${DAY_OF_WEEK}" -eq 7 ]; then
       WEEKLY_FILE="${WEEKLY_DIR}/${DB_NAME}-weekly-${TIMESTAMP}.dump.zst"
       cp -p "${DAILY_FILE}" "${WEEKLY_FILE}"
       chmod 600 "${WEEKLY_FILE}"
       echo "[$(date -Iseconds)] Weekly backup created: ${WEEKLY_FILE}"
   fi

   find "${DAILY_DIR}" -type f -name "${DB_NAME}-daily-*.dump.zst" -mtime +7 -delete
   find "${WEEKLY_DIR}" -type f -name "${DB_NAME}-weekly-*.dump.zst" -mtime +28 -delete
   echo "[$(date -Iseconds)] Backup job completed successfully."
   EOF

   sudo chown root:root /usr/local/bin/backup-matchmate-db.sh
   sudo chmod 755 /usr/local/bin/backup-matchmate-db.sh
   sudo mkdir -p /var/log/postgresql
   sudo chown postgres:postgres /var/log/postgresql
   ```

2. Add to `postgres` user crontab:
   ```bash
   sudo crontab -u postgres -l 2>/dev/null | { cat; echo "0 2 * * * /usr/local/bin/backup-matchmate-db.sh >> /var/log/postgresql/backup.log 2>&1"; } | sudo crontab -u postgres -
   ```

3. Test run the backup immediately:
   ```bash
   sudo -u postgres /usr/local/bin/backup-matchmate-db.sh
   sudo ls -lh /var/backups/postgresql/daily/
   ```

4. Verify restore procedure against a temporary test database:
   ```bash
   LATEST_BACKUP=$(sudo ls -t /var/backups/postgresql/daily/*.dump.zst | head -n 1)
   sudo -u postgres createdb matchmate_restore_test
   sudo -u postgres bash -c "zstd -dc '${LATEST_BACKUP}' | pg_restore -d matchmate_restore_test --no-owner"
   sudo -u postgres psql -d matchmate_restore_test -c "\dt"
   sudo -u postgres dropdb matchmate_restore_test
   echo "Backup and restore test succeeded!"
   ```

---

## Step G: GitHub Repository Configuration

In GitHub (`https://github.com/pkoley06/matchmate_backend`):

1. Go to **Settings -> Environments -> New environment** and name it:
   ```
   production
   ```
2. In the `production` environment, under **Environment secrets**, add:
   * `OCI_HOST`: Your VM Public IP (e.g., `123.45.67.89`)
   * `OCI_SSH_KEY`: The entire contents of `./id_ed25519_deploy` (including `-----BEGIN OPENSSH PRIVATE KEY-----` and `-----END OPENSSH PRIVATE KEY-----`)
   * `OCI_KNOWN_HOSTS`: The exact single-line output from `./known_hosts_entry` (e.g., `123.45.67.89 ssh-ed25519 AAAAC3...`)

3. *(Optional)* Under **Deployment branches**, configure rule to allow `main` and `dev`.

4. **Delete the private key from your local machine**:
   ```bash
   rm -f ./id_ed25519_deploy ./id_ed25519_deploy.pub ./known_hosts_entry
   ```

---

## Step H: First Deploy, Verification, Rollbacks & Troubleshooting

### 1. Trigger the First Deployment
Go to GitHub Actions in your repo:
1. Select **CI & Deploy** workflow.
2. Click **Run workflow** (branch: `main` or `dev`).
3. The pipeline will:
   * Build the jar with Gradle (Temurin 25).
   * Run the test suite.
   * Build and push the ARM64 container image to GHCR.
   * Connect over SSH to trigger `/usr/local/bin/matchmate-backend-deploy <SHA>`.

### 2. Verify on the VM
```bash
# Check container status
sudo docker compose -f /opt/matchmate-backend/compose.yml ps

# Test the health endpoint
curl -i http://127.0.0.1:8080/health

# Test public IP access via Nginx
curl -i http://<VM_PUBLIC_IP>/health

# Inspect real-time logs via journald
sudo journalctl -u docker -t matchmate-backend -f
```

### 3. How to Rollback to a Previous Commit
1. Go to **Actions -> Deploy**.
2. Click **Run workflow**.
3. Enter the full 40-character commit SHA of the known healthy version.
4. Click **Run workflow**. The script will pull the target image, verify health, and atomically swap it.

### 4. Troubleshooting Table

| Symptom | Root Cause | Solution |
| :--- | :--- | :--- |
| `Permission denied (publickey)` in CI | Mismatch in `OCI_SSH_KEY` or `authorized_keys` | Ensure pubkey in `/home/deploy/.ssh/authorized_keys` matches `OCI_SSH_KEY` and permissions are `600`. |
| `Host key verification failed` | `OCI_KNOWN_HOSTS` mismatch or missing IP | Re-run `ssh-keyscan -H <VM_IP>` and update secret in GitHub. |
| `sudo: a password is required` | `/etc/sudoers.d/matchmate-backend-deploy` missing or invalid | Run `sudo visudo -c` and check that `deploy ALL=(ALL) NOPASSWD: /usr/local/bin/matchmate-backend-deploy` exists. |
| Deployment times out after 120s | Container failed to start or health endpoint returning error | Run `sudo docker compose -f /opt/matchmate-backend/compose.yml logs app` to inspect Spring Boot stack trace. |
| `Firebase service account file not found` | File missing or path wrong in `/etc/matchmate-backend.env` | Verify `/etc/matchmate-backend/firebase-service-account.json` exists and is readable. |
| `FATAL: password authentication failed for user` | Wrong `DB_PASSWORD` or `DB_USER` | Verify `/etc/matchmate-backend.env` matches PostgreSQL credentials on the VM. |
| Port 80 unreachable from browser | OCI VCN Ingress or VM host iptables blocking | Run iptables command in Step D and verify OCI Security List. |

## Step I: Object Storage Setup for Photo Uploads

Profile photos and listing photos need a real bucket in production — the app's built-in
`local` storage fallback writes to disk, which doesn't survive container restarts (the prod
container runs `read_only: true` with no persistent volume) and only exists so the feature
works out of the box with zero cloud setup during development.

The backend talks to any S3-compatible bucket (AWS S3, OCI Object Storage, MinIO, ...) through
one client — switching providers later is just editing env vars and restarting the container,
no code changes. Below is the OCI Object Storage path, since that's where this app is deployed.

### 1. Create the bucket
1. OCI Console → **Storage → Object Storage & Archive Storage → Buckets**.
2. **Create Bucket** → name it e.g. `matchmate-bucket`.
3. **Visibility: Public** — profile/listing photos aren't sensitive, and this avoids needing
   pre-authenticated requests just to display an image. Leave everything else default.
   > [!NOTE]
   > A new bucket defaults to **Private**. The Details tab has no visibility toggle — set it from
   > the bucket's **Policies** tab → **Edit Visibility** → **Public** → Save.
4. Open the bucket's details page and note its **Namespace** (shown near the top) and the
   **Region** you created it in (e.g. `ap-mumbai-1`).

### 2. Generate S3-compatible credentials
OCI's S3-compatibility API uses "Customer Secret Keys" as access/secret key pair:
1. OCI Console → your profile icon (top right) → **My Profile**.
2. **Customer Secret Keys** (left sidebar) → **Generate Secret Key**.
3. Copy the **Access Key** immediately, and the **Secret Key** shown once — it cannot be
   retrieved again after you leave the page (generate a new one if you lose it).

### 3. Fill in `/etc/matchmate-backend.env` on the VM
Using the namespace/region from step 1 and the keys from step 2 (see
`deploy/matchmate-backend.env.example` for the full block). This deployment's actual bucket
(`ap-mumbai-1`, namespace `bmlk46grfoot`, bucket `matchmate-bucket`) looks like:
```bash
STORAGE_PROVIDER=s3
STORAGE_BUCKET=matchmate-bucket
STORAGE_REGION=ap-mumbai-1
STORAGE_ENDPOINT=https://bmlk46grfoot.compat.objectstorage.ap-mumbai-1.oraclecloud.com
STORAGE_ACCESS_KEY=<access key from step 2>
STORAGE_SECRET_KEY=<secret key from step 2>
STORAGE_PATH_STYLE_ACCESS=true
STORAGE_PUBLIC_BASE_URL=https://objectstorage.ap-mumbai-1.oraclecloud.com/n/bmlk46grfoot/b/matchmate-bucket/o
```
If you provision a different bucket/region/namespace, substitute your own values from step 1 and
step 2 accordingly.

> [!WARNING]
> `STORAGE_ENDPOINT` and `STORAGE_PUBLIC_BASE_URL` use *different* OCI hostname shapes — mixing
> them up causes a DNS resolution failure when the app/client tries to load an image:
> - `STORAGE_ENDPOINT` (S3-compat API, used for uploads): namespace is a hostname **prefix** —
>   `https://<namespace>.compat.objectstorage.<region>.oraclecloud.com`
> - `STORAGE_PUBLIC_BASE_URL` (native public read URL): namespace is a **path segment**, not a
>   hostname prefix — `https://objectstorage.<region>.oraclecloud.com/n/<namespace>/b/<bucket>/o`

### 4. Restart and verify
```bash
sudo docker compose -f /opt/matchmate-backend/compose.yml restart app
sudo docker compose -f /opt/matchmate-backend/compose.yml logs app | grep -i storage
```
No `IllegalStateException` about a missing `STORAGE_*` variable means it started cleanly. Then
upload a profile photo or listing photo from the app and confirm the returned URL loads in a
browser — it should 200 with the image, with no authentication required.

### Switching to a different provider later
Any S3-compatible service works the same way — just change `STORAGE_ENDPOINT`,
`STORAGE_REGION`, `STORAGE_BUCKET`, the keys, and `STORAGE_PUBLIC_BASE_URL` to that provider's
values (e.g. real AWS S3 can drop `STORAGE_ENDPOINT` entirely and use its normal bucket
region/name). Nothing else changes.

## Step J: 30-Day Grace-Period Account Deletion

"Delete account" no longer purges data immediately — it schedules a purge after an
admin-configurable grace period (default 30 days), during which signing in again shows a
cancel-or-sign-out confirmation in the app instead of either silently letting the user back in
or leaving them on a stuck dashboard.

### 1. What ships automatically on deploy
No manual step is required for a normal `git push` → CI deploy:
- **Flyway migration `V13__add_pending_deletion.sql`** auto-applies on container boot
  (`spring.flyway.enabled: true` in `application.yml`) — adds `deletion_requested_at` /
  `scheduled_purge_at` to `app_users` and `deletion_grace_period_days` (default `30`) to
  `app_branding`.
- **`AccountPurgeScheduler`** (new — first scheduled job in this codebase, enabled via
  `@EnableScheduling`) runs daily at **03:00 server time** inside the running container and
  purges (resets-in-place, never hard-deletes) any account whose grace period has lapsed, except
  suspended accounts, which it always skips.

### 2. Verify after deploying
```bash
# Confirm the new columns exist
PGPASSWORD='matchmate' psql -h 127.0.0.1 -U dev_user -d matchmate_db -c \
  "\d app_users" | grep -E "deletion_requested_at|scheduled_purge_at"
PGPASSWORD='matchmate' psql -h 127.0.0.1 -U dev_user -d matchmate_db -c \
  "SELECT deletion_grace_period_days FROM app_branding WHERE id = 1;"

# Confirm the scheduler is registered (no output expected here beyond startup logs —
# the job only logs when it actually finds accounts due for purge)
sudo docker compose -f /opt/matchmate-backend/compose.yml logs app | grep -i "AccountPurgeScheduler\|flyway" -i
```

### 3. Changing the grace period
Admins change it via the existing branding endpoints — no redeploy needed:
```
PUT /api/v1/admin/branding
{ "deletionGracePeriodDays": 14, ... (other required branding fields) }
```
Only affects accounts that request deletion *after* the change; an account already
`PENDING_DELETION` keeps the `scheduled_purge_at` it was given when it requested deletion.

### 4. Server timezone matters for the purge cron
`@Scheduled(cron = "0 0 3 * * *")` runs at 03:00 in the **container/VM's local timezone**, not
UTC unless the VM is set to UTC. Check with `date` on the VM; if you need a specific wall-clock
time regardless of VM timezone, the cron expression would need a `TimeZone` attribute added in
`AccountPurgeScheduler.java` — not currently set.
