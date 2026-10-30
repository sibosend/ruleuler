# Docker Quick Start

Only Docker Desktop is required; no local Java, Node.js, or MySQL installation is needed.

## 1. Prepare Configuration

```bash
git clone https://github.com/sibosend/ruleuler.git
cd ruleuler
cp .env.example .env
```

The default values in `.env` work out of the box for local development. In production, update the database passwords and JWT secret.

## 2. Start Services

```bash
docker compose up -d --build
```

On the first run, Docker Compose automatically:

- Pulls base images (JDK, Node, MySQL)
- Builds frontend assets (`console-js` + `admin`)
- Builds backend services (`server` + `client`)
- Initializes the database (schema + sample project)
- Starts the `server`, `client`, and `mysql` containers

!!! info "First Build Time"
    The initial build takes about 8–15 minutes (mostly downloading dependencies). Subsequent builds with cache take 1–3 minutes.

## 3. Access Services

| Service | URL | Description |
|---------|-----|-------------|
| Admin Console | http://localhost:16009/admin/ | Log in to edit and manage rules |
| Client API | http://localhost:16001 | Rule execution endpoint |
| MySQL | 127.0.0.1:3307 | User `ruleuler` / password in `.env` |

Default credentials: `admin` / `asdfg@1234`

## 4. Verify

```bash
curl -X POST http://localhost:16001/process/airport_gate_allocation_db/gate_pkg/gate_allocation_flow \
  -H 'Content-Type: application/json' \
  -d '{
  "FlightInfo": {
    "aircraft_type": "A380",
    "arrival_time": 8,
    "is_international": true,
    "passenger_count": 260
  },
  "GateResult": {}
}'
```

Expected response: `status: 200`, with `gate_type`, `assigned_gate`, and other fields populated in `data`.

## 5. Common Commands

```bash
docker compose logs -f          # Follow logs
docker compose down             # Stop containers
docker compose up -d --build    # Rebuild and start
```

## Next Steps

- Developing locally? See [Local Development](local-dev.md)
- Learning core concepts? See [Core Concepts](../guide/concepts.md)

