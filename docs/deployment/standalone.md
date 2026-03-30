# 独立部署

不使用 Docker，直接在服务器上运行。

## 前置条件

| 依赖 | 版本 |
|------|------|
| Java | 21+ |
| MySQL | 8.0+ |

## 1. 构建

```bash
./build.sh
```

构建产物输出到 `dist/` 目录。

## 2. 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleuler_data DEFAULT CHARACTER SET utf8mb4"
mysql -u root -p ruleuler_data < deploy/init.sql
```

## 3. 配置

通过环境变量配置数据库连接和认证参数，参考 [配置说明](../getting-started/configuration.md)。

## 4. 启动

```bash
# 启动 server 和 client
java -jar dist/ruleuler-server.jar &
java -jar dist/ruleuler-client.jar &
```

## 5. 反向代理（可选）

生产环境建议使用 Nginx 反向代理：

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
