# Standalone Deployment

Run RulEuler directly on a host without Docker.

## Prerequisites

| Dependency | Version |
|------------|---------|
| Java | 21+ |
| MySQL | 8.0+ |

## 1. Build

```bash
./build.sh
```

Build artifacts are generated under the `dist/` directory.

## 2. Initialize Database

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleuler_data DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p ruleuler_data < deploy/init.sql
```

## 3. Configure

Set database connection and authentication parameters via environment variables. See [Configuration](../getting-started/configuration.md).

## 4. Start Services

```bash
# Start server and client
java -Dloader.path=dist/server/lib/ -jar dist/server/app.jar &
java -Dloader.path=dist/client/lib/ -jar dist/client/app.jar &
```

## 5. Reverse Proxy (Optional)

In production, placing Nginx in front of the services is recommended:

```nginx
server {
    listen 80;
    server_name ruleuler.example.com;

    location /admin/ {
        proxy_pass http://127.0.0.1:16009;
    }

    location / {
        proxy_pass http://127.0.0.1:16001;
    }
}
```

