# 贡献指南

感谢你对 RulEuler 的关注。

## 开发环境

参考 [本地开发环境](getting-started/local-dev.md) 搭建开发环境。

## 分支策略

- `main` — 稳定版本
- `dev` — 开发分支
- 功能分支从 `dev` 创建，完成后提交 PR 合并回 `dev`

## 提交规范

```
feat: 新功能
fix: 修复
docs: 文档
refactor: 重构
test: 测试
chore: 构建/工具
```

示例：`feat: 支持 REA 表达式中的函数调用`

## 项目约定

- 不轻易修改 `ruleuler-core`
- 存储逻辑统一使用 db 模式
- 不修改 `/urule/` 下的旧接口，目标是逐步替代
- 前端注意避免重复网络请求（StrictMode 下）

## 测试

提交 PR 前请确保测试通过：

```bash
# 前端
cd ruleuler-admin && pnpm test

# 后端
mvn test -pl ruleuler-server
```

## 报告问题

通过 GitHub Issues 提交，请包含：

- 复现步骤
- 预期行为 vs 实际行为
- 环境信息（OS、Java 版本、浏览器）
