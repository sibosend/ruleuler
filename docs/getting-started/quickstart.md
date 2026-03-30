# Docker 快速启动

只需安装 Docker Desktop，无需 Java/Node/MySQL。

## 1. 准备配置

```bash
git clone https://github.com/sibosend/ruleuler.git
cd ruleuler
cp .env.example .env
```

`.env` 里的默认值可以直接用于本地开发，生产环境需修改密码和 JWT secret。

## 2. 启动

```bash
docker compose up -d --build
```

首次运行会自动完成：

- 拉取基础镜像（JDK、Node、MySQL）
- 编译前端（console-js + admin）
- 编译后端（server + client）
- 初始化数据库（建表 + 示例项目）
- 启动 server、client、mysql 三个容器

!!! info "首次构建时间"
    首次约 8-15 分钟（主要是下载依赖），后续有缓存只需 1-3 分钟。

## 3. 访问

| 服务 | 地址 | 说明 |
|------|------|------|
| 管理后台 | http://localhost:16009/admin/ | 登录后可编辑规则 |
| 客户端 API | http://localhost:16001 | 规则执行接口 |
| MySQL | 127.0.0.1:3307 | 用户 ruleuler / 密码见 .env |

默认账号：`admin` / `asdfg@1234`

## 4. 验证

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

预期返回 `status: 200`，`data` 中包含 `gate_type`、`assigned_gate` 等字段。

## 5. 常用操作

```bash
docker compose logs -f          # 查看日志
docker compose down             # 停止
docker compose up -d --build    # 重新构建并启动
```

## 下一步

- 需要本地开发？参考 [本地开发环境](local-dev.md)
- 了解规则引擎核心概念？参考 [核心概念](../guide/concepts.md)
