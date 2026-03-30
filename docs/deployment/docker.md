# Docker 部署

## 前置条件

- Docker Engine 20+
- Docker Compose v2

## 生产环境配置

```bash
cp .env.example .env
```

编辑 `.env`，修改以下关键配置：

```bash
# 数据库密码（必改）
DB_PASSWORD=your_secure_password

# JWT 密钥（必改，≥32 字节）
AUTH_JWT_SECRET=your_jwt_secret_at_least_32_bytes

# Token 过期时间
AUTH_JWT_EXPIRATION_HOURS=24
```

!!! danger "安全提醒"
    生产环境必须修改默认密码和 JWT secret。

## 启动

```bash
docker compose up -d --build
```

## 数据持久化

MySQL 数据通过 Docker volume 持久化，容器重启不会丢失数据。

## 日志

```bash
# 查看所有服务日志
docker compose logs -f

# 查看特定服务
docker compose logs -f server
docker compose logs -f client
```

## 更新

```bash
docker compose down
git pull
docker compose up -d --build
```
