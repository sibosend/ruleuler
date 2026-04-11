# 上线管理

## 概述

上线管理模块为知识包的发布提供审批流程控制，确保规则变更经过测试验证和人工审核后才能上线生效。

核心流程：**提交 → 自动测试 → 审核 → 上线**。

---

## 审批流程

```
提交发布申请
    │
    ├─ 有测试用例包 ──→ TESTING（异步执行自动测试）──→ PENDING（待审核）
    │
    └─ 无测试用例包 ──→ PENDING（待审核）
                              │
                    ┌─────────┼─────────┐
                    ↓                   ↓
               APPROVED            REJECTED
              （待上线）            （已拒绝）
                    │
                    ↓
               执行上线
                    │
              ┌─────┼─────┐
              ↓           ↓
          PUBLISHED   PUBLISH_FAILED
          （已上线）   （上线失败，可重试）
```

### 状态说明

| 状态 | 说明 |
|------|------|
| TESTING | 自动测试执行中，完成后自动流转到 PENDING |
| PENDING | 待审核，等待审批人操作 |
| APPROVED | 审核通过，等待上线 |
| REJECTED | 审核拒绝 |
| PUBLISHED | 已上线 |
| PUBLISH_FAILED | 上线失败，可重新执行上线 |

---

## 提交发布

权限要求：`pack:publish:submit`

提交时系统自动完成：

1. **唯一性检查**：同一知识包不允许同时存在多个 PENDING 状态的审批单
2. **内容快照**：将项目下所有决策资源的当前 XML 内容存入 `ruleuler_publish_snapshot`
3. **内容级 Diff**：对比当前内容与上次快照，计算组件级变更
4. **自动测试**：如果知识包关联了测试用例包，异步执行测试

### 内容级 Diff

Diff 计算基于 XML 内容对比，而非版本号。对比粒度：

**组件级**：识别 ADDED / MODIFIED / DELETED 的决策组件

**规则行级**：对 MODIFIED 的组件，按资源类型解析到具体规则：

| 资源类型 | 解析策略 |
|----------|----------|
| 决策表 (.dt.xml) | 按 cell 的 row 属性分组 |
| 规则集 (.rs.xml) | 按 rule 的 name 属性 |
| REA 规则 (.rea.xml) | 按 rea-item 的 name 属性 |
| 决策流 (.rl.xml) | 按 node 的 name 属性 |
| 决策树 (.dtree.xml) | 按 node 的 var/var-label 属性 |
| 其他 | 按顶层子元素 tag + 索引 |

**字段级**：对 MODIFIED 的规则行，提取具体字段变化：

- 条件变更：`arrival_time >= : 6.8 → 6.9`
- 赋值变更：`assigned_gate = : A区-101 → A区-102`
- 单元格变更：`R0C2 : B737 → A320`

### 自动测试集成

提交时如果检测到知识包关联了测试用例包：

1. 审批单初始状态为 `TESTING`
2. 异步线程执行最新用例包的全部测试用例
3. 测试完成后（无论通过或失败），状态自动流转到 `PENDING`
4. 审批详情中展示测试结果摘要（通过数 / 失败数）和报告链接

---

## 审核

权限要求：`pack:publish:approve`

审批人可以：

- **通过**：状态流转到 APPROVED，可附加审批意见（≤500 字）
- **拒绝**：状态流转到 REJECTED，可附加拒绝原因

审批详情页展示：

- 基本信息：知识包、提交人、时间、状态
- 变更说明（提交人填写）
- 测试结果摘要
- Diff 详情：按组件类型分组，可展开查看规则行级和字段级变更

---

## 上线

权限要求：`pack:publish:submit`

仅 APPROVED 或 PUBLISH_FAILED 状态的审批单可执行上线。

上线流程：

1. 调用规则引擎的知识包发布接口，推送到客户端运行时
2. 成功后创建新的内容快照（作为下次 Diff 的基准）
3. 失败时记录失败原因，状态置为 PUBLISH_FAILED，支持重试

---

## 页面视图

前端提供四个视图，通过路由区分：

| 视图 | 路径 | 说明 |
|------|------|------|
| 待审核 | `/releases/pending` | 审批人视角，展示 PENDING 状态的审批单 |
| 待上线 | `/releases/pending-publish` | 展示 APPROVED 状态的审批单 |
| 我的申请 | `/releases/my` | 当前用户提交的所有审批单 |
| 全部 | `/releases/all` | 所有审批单，支持状态筛选 |

---

## 数据表

| 表名 | 说明 |
|------|------|
| `ruleuler_publish_approval` | 审批单主表 |
| `ruleuler_publish_approval_diff` | 组件级变更明细，details 字段存储规则行级 JSON |
| `ruleuler_publish_snapshot` | 内容快照，snapshot_data 为 JSON（path → XML 内容） |

---

## API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/approvals` | 提交发布申请 |
| GET | `/api/approvals` | 审批单列表（支持 project/status/submitter 筛选） |
| GET | `/api/approvals/{id}` | 审批单详情（含 Diff 和测试结果） |
| PUT | `/api/approvals/{id}/approve` | 审核通过 |
| PUT | `/api/approvals/{id}/reject` | 审核拒绝 |
| PUT | `/api/approvals/{id}/publish` | 执行上线 |
| POST | `/api/approvals/{id}/recalc-diff` | 重新计算 Diff（修复历史数据） |
