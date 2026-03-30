# 配置说明

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DB_URL` | 数据库连接 URL | `jdbc:mysql://localhost:3306/ruleuler_data` |
| `DB_USERNAME` | 数据库用户名 | `root` |
| `DB_PASSWORD` | 数据库密码 | - |
| `AUTH_JWT_SECRET` | JWT 签名密钥（≥32 字节） | - |
| `AUTH_JWT_EXPIRATION_HOURS` | Token 过期时间（小时） | `24` |
| `ADMIN_BASE_URL` | admin 前端地址 | `http://localhost:16009` |
| `URULE_REPO_DIR` | JCR 仓库目录 | `/tmp/urule-repo` |

!!! warning "生产环境"
    生产环境必须修改 `AUTH_JWT_SECRET` 和数据库密码，不要使用默认值。

## .env 文件

项目根目录的 `.env` 文件用于 Docker Compose 和本地开发：

```bash
cp .env.example .env
```

## 端口配置

| 服务 | 默认端口 | 配置方式 |
|------|----------|----------|
| Server（管理后台） | 16009 | `--server-port` 或 `SERVER_PORT` |
| Client（规则执行） | 16001 | `--client-port` 或 `CLIENT_PORT` |
| MySQL（Docker） | 3307 | `docker-compose.yml` |
