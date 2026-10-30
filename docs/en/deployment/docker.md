# Docker Deployment

## Prerequisites

- Docker Engine 20+
- Docker Compose v2

## Production Configuration

```bash
cp .env.example .env
```

Edit `.env` and update the following critical settings:

```bash
# Database password (required)
DB_PASSWORD=your_secure_password

# JWT secret (required, ≥32 bytes)
AUTH_JWT_SECRET=your_jwt_secret_at_least_32_bytes

# Token expiration time
AUTH_JWT_EXPIRATION_HOURS=24
```

!!! danger "Security Notice"
    Always change the default passwords and `AUTH_JWT_SECRET` in production environments.

## Start Containers

```bash
docker compose up -d --build
```

## Data Persistence

MySQL data is persisted via Docker volumes so data is retained across container restarts.

## Logs

```bash
# Follow all service logs
docker compose logs -f

# Follow a specific service
docker compose logs -f server
docker compose logs -f client
```

## Upgrade

```bash
docker compose down
git pull
docker compose up -d --build
```

