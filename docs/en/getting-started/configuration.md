# Configuration

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | Database JDBC URL | `jdbc:mysql://localhost:3306/ruleuler_data` |
| `DB_USERNAME` | Database username | `root` |
| `DB_PASSWORD` | Database password | - |
| `AUTH_JWT_SECRET` | JWT signing key (≥32 bytes) | - |
| `AUTH_JWT_EXPIRATION_HOURS` | Token expiration time (hours) | `24` |
| `ADMIN_BASE_URL` | Admin frontend base URL | `http://localhost:16009` |
| `URULE_REPO_DIR` | JCR repository directory | `/tmp/urule-repo` |
| `RULEULER_PROJECTS` | Comma-separated project list for client startup function JAR pre-pull | `airport_gate_allocation_db` |

!!! warning "Production Environment"
    Always change `AUTH_JWT_SECRET` and database passwords in production; do not use default values.

## `.env` File

The `.env` file in the project root is used by Docker Compose and local development scripts:

```bash
cp .env.example .env
```

## Port Configuration

| Service | Default Port | Configuration |
|---------|--------------|---------------|
| Server (Admin Console) | 16009 | `--server-port` or `SERVER_PORT` |
| Client (Rule Execution) | 16001 | `--client-port` or `CLIENT_PORT` |
| MySQL (Docker) | 3307 | `docker-compose.yml` |

