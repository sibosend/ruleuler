# RulEuler 规则引擎

中文 | [English](README.en.md)

基于 rete 算法的规则引擎，支持 db 和 jcr 两种存储方式，默认 db 存储。

![REA 表达式编辑器](docs/assets/images/rea.png)

默认账号：admin / asdfg@1234

## 系统功能

- **规则编辑**：决策表、决策树、决策流、规则集、评分卡、REA 表达式等可视化编辑
- **知识包管理**：项目级资源组织，支持子文件夹，知识包打包与版本管理
- **依赖分析**：可视化系统依赖图谱，提供所有决策资源的上下游关联血缘查找与受影响面推算
- **自动测试**：用例包定义、批量执行、测试报告
- **变量监控**：执行记录查询与单次运行追踪（Execution Trace）、变量趋势分析、周期对比、PSI 分布稳定性、枚举漂移检测、分时走势、异常下钻
- **上线管理**：知识包发版审批流，基于百分比和规则条件的灰度发布路由 / A/B 测试，内容级 diff 对比（行级+字段级变更），快照版本防改动锁定
- **RBAC 权限**：角色权限控制，菜单级 + 操作级鉴权
- **系统审计**：记录面向账号管理、各类组件数据配置及发版核心链路等管理端系统操作的全貌溯源审计日志

## 项目结构

```
├── ruleuler-core              # 核心引擎（rete 算法、模型定义），不轻易修改
├── ruleuler-console           # 管理后台 Servlet（编辑器页面渲染、文件操作）
├── ruleuler-console-js        # 编辑器前端（webpack + React，产出 *.bundle.js）
├── ruleuler-admin             # 新管理后台前端（Vite + React + Ant Design）
├── ruleuler-server            # 服务端（Spring Boot，JWT 认证，REST API）
├── ruleuler-client            # 客户端（Spring Boot，规则执行，对外 API）
├── deploy/                 # SQL 初始化脚本
├── docs/                   # API 文档
├── dist/                   # 构建产物
└── logs/                   # 运行日志
```

---

## 方式一：Docker Compose 启动（推荐，零本地依赖）

只需安装 Docker Desktop，无需 Java/Node/MySQL。

### 1. 准备配置

```bash
cp .env.example .env
```

`.env` 里的默认值可以直接用于本地开发，生产环境需修改密码和 JWT secret。

### 2. 启动

```bash
docker compose up -d --build
```

首次运行会自动完成：
- 拉取基础镜像（JDK、Node、MySQL）
- 编译前端（console-js + admin）
- 编译后端（server + client）
- 初始化数据库（建表 + 示例项目）
- 启动 server、client、mysql 三个容器

首次约 8-15 分钟（主要是下载依赖），后续有缓存只需 1-3 分钟。

### 3. 访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 管理后台 | http://localhost:16009/admin/ | 登录后可编辑规则 |
| 客户端 API | http://localhost:16001 | 规则执行接口 |
| MySQL | 127.0.0.1:3307 | 用户 ruleuler / 密码见 .env |

### 4. 验证

```bash
# 调用示例项目的决策流
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

预期返回 `status: 200`，`data` 中包含 `gate_type`、`assigned_gate` 等字段。

### 5. 常用操作

```bash
docker compose logs -f          # 查看日志
docker compose down             # 停止
docker compose up -d --build    # 重新构建并启动
```

---

## 方式二：本地开发启动

### 环境要求

| 依赖 | 版本 | 安装方式 |
|------|------|----------|
| Java | 21+ | `brew install openjdk@21` 或 jenv 管理 |
| Maven | 3.9+ | `brew install maven` |
| Node.js | 20+ | `brew install node@20` |
| pnpm | 9+ | `npm install -g pnpm@9` |
| MySQL | 8.0+ | `brew install mysql` 或 Docker 运行 |

### 1. 初始化数据库

```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ruleuler_data DEFAULT CHARACTER SET utf8mb4"

# 建表 + RBAC 初始数据
mysql -u root -p ruleuler_data < deploy/init.sql

# 导入示例项目（可选）
mysql -u root -p ruleuler_data < deploy/example_airport_gate.sql
```

### 2. 修改数据库连接

编辑 `ruleuler-server/src/main/resources/application-dev.yml`，确认 datasource 配置与本地 MySQL 一致。

### 3. 一键构建 + 启动

```bash
./start.sh dev
```

自动完成：构建 console-js → 构建 admin → 打包 server → 打包 client → 启动两个进程。

支持自定义端口：
```bash
./start.sh dev --server-port 18009 --client-port 18001
```

### 4. 访问

| 服务 | 地址 |
|------|------|
| 管理后台 | http://localhost:16009/admin/ |
| 客户端 API | http://localhost:16001 |

---

## 客户端 API

### 执行决策流

```bash
curl -X POST http://localhost:16001/process/{project}/{packageId}/{flowId} \
  -H 'Content-Type: application/json' \
  -d '{ "FlightInfo": { ... }, "GateResult": {} }'
```

请求体按变量类别分组，类别名与知识包定义一致。返回只包含被规则修改的字段。

### 变量提取

```bash
curl -X POST http://localhost:16001/inspector/variables/{project}/{packageId}
```

---

## MCP Server

ruleuler-client 内置 MCP Server，通过 Streamable HTTP 协议暴露两个 Tool，供 AI 助手直接调用规则引擎：

| Tool | 功能 |
|------|------|
| `execute_rule` | 执行决策流，返回决策结果 |
| `inspect_variables` | 查看知识包变量定义 |

端点：`POST http://localhost:16001/mcp`

配置（`application-dev.yml` 已默认开启）：

```yaml
ruleuler:
  mcp:
    enabled: true
```

Claude Desktop / Claude Code / Cursor 等客户端配置示例：

```json
{
  "mcpServers": {
    "ruleuler": {
      "url": "http://localhost:16001/mcp"
    }
  }
}
```

详细接入指南见 [MCP Server 接入指南](docs/guide/mcp-server.md)。

---

## 发版

```bash
./build.sh          # patch 版本 +1，构建产物到 dist/
```

---

## 存储模式

| 模式 | 前缀 | 说明 |
|------|------|------|
| db | `dbr:` | 存储在 `ruleuler_rule_file` 表，支持子文件夹 |
| jcr | 无前缀 | 存储在 JCR 仓库（Jackrabbit） |

创建项目时选择存储类型，不可混用。

---

## 关键文档

- API 列表：docs/api.md
- 内部 API：docs/api.internal.md
- REA 表达式语法：docs/rea-expression.md

## 测试

- 测试 fixture project：`test_project_fixture_001`
- 前端测试：`cd ruleuler-admin && pnpm test`
- 后端测试：`mvn test -pl ruleuler-server`

## 环境变量（Prod 部署）

| 变量 | 说明 |
|------|------|
| DB_URL | 数据库连接 |
| DB_USERNAME / DB_PASSWORD | 数据库账号 |
| AUTH_JWT_SECRET | JWT 签名密钥（≥32字节） |
| AUTH_JWT_EXPIRATION_HOURS | Token 过期时间 |
| ADMIN_BASE_URL | admin 前端地址 |
| URULE_REPO_DIR | JCR 仓库目录 |

## License

[Apache License 2.0](LICENSE)

本项目基于 [URule](https://github.com/youseries/urule)（Copyright Bstek）二次开发，详见 [NOTICE](NOTICE)。
