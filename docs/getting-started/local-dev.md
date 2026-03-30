# 本地开发环境

## 环境要求

| 依赖 | 版本 | 安装方式 |
|------|------|----------|
| Java | 21+ | `brew install openjdk@21` 或 jenv 管理 |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 20+ | `brew install node@20` |
| pnpm | 9+ | `npm install -g pnpm@9` |
| MySQL | 8.0+ | `brew install mysql` 或 Docker 运行 |

## 1. 初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleuler_data DEFAULT CHARACTER SET utf8mb4"

# 建表 + RBAC 初始数据
mysql -u root -p ruleuler_data < deploy/init.sql

# 导入示例项目（可选）
mysql -u root -p ruleuler_data < deploy/example_airport_gate.sql
```

## 2. 修改数据库连接

编辑 `ruleuler-server/src/main/resources/application-dev.yml`，确认 datasource 配置与本地 MySQL 一致。

## 3. 一键构建 + 启动

```bash
./start.sh dev
```

自动完成：构建 console-js → 构建 admin → 打包 server → 打包 client → 启动两个进程。

支持自定义端口：

```bash
./start.sh dev --server-port 18009 --client-port 18001
```

## 4. 访问

| 服务 | 地址 |
|------|------|
| 管理后台 | http://localhost:16009/admin/ |
| 客户端 API | http://localhost:16001 |

## 前端单独开发

```bash
cd ruleuler-admin
pnpm install
pnpm dev
```

前端开发服务器默认代理 API 请求到 `localhost:16009`，需要先启动后端。

## 测试

```bash
# 前端测试
cd ruleuler-admin && pnpm test

# 后端测试
mvn test -pl ruleuler-server
```
