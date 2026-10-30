# Local Development

## Prerequisites

| Dependency | Version | Installation |
|------------|---------|--------------|
| Java | 21+ | `brew install openjdk@21` or manage via jenv |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 20+ | `brew install node@20` |
| pnpm | 9+ | `npm install -g pnpm@9` |
| MySQL | 8.0+ | `brew install mysql` or run via Docker |

## 1. Initialize Database

```bash
# Create database
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleuler_data DEFAULT CHARACTER SET utf8mb4"

# Create tables + initial RBAC data
mysql -u root -p ruleuler_data < deploy/init.sql

# Import sample project (optional)
mysql -u root -p ruleuler_data < deploy/example_airport_gate.sql
```

## 2. Configure Database Connection

Edit `ruleuler-server/src/main/resources/application-dev.yml` and verify that the datasource settings match your local MySQL instance.

## 3. One-Command Build & Start

```bash
./start.sh dev
```

This script automatically builds `console-js` → builds `admin` → packages `server` → packages `client` → installs `function-sdk` → starts both processes.

Custom ports are supported:

```bash
./start.sh dev --server-port 18009 --client-port 18001
```

## 4. Access Services

| Service | URL |
|---------|-----|
| Admin Console | http://localhost:16009/admin/ |
| Client API | http://localhost:16001 |

## Standalone Frontend Development

```bash
cd ruleuler-admin
pnpm install
pnpm dev
```

The Vite dev server proxies API requests to `localhost:16009` by default, so make sure the backend server is running first.

## Running Tests

```bash
# Frontend tests
cd ruleuler-admin && pnpm test

# Backend tests
mvn test -pl ruleuler-server
```

