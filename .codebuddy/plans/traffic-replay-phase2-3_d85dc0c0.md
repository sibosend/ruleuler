---
name: traffic-replay-phase2-3
overview: 完成流量回放 Phase 2 剩余（i18n）和 Phase 3 全部（审批集成+权限+配置+菜单），覆盖 spec 需求 10-12
todos:
  - id: i18n-replay
    content: 补全 i18n 翻译键（zh-CN.json + en.json 的 route/project/replay 相关键）
    status: completed
  - id: migration-approval-replay
    content: "创建 approval 表 migration（V003: replay_task_id 字段）"
    status: completed
  - id: approval-model-dao
    content: 扩展 Approval.java + ApprovalDao（replayTaskid 映射、insert、update 方法）
    status: completed
    dependencies:
      - migration-approval-replay
  - id: approval-service-extend
    content: 扩展 ApprovalService（注入 ReplayService，submit 并行触发 autotest+replay）
    status: completed
    dependencies:
      - approval-model-dao
  - id: release-page-replay
    content: 审批详情页展示 replay 状态（ApprovalVO 扩展 + ReleaseListPage replaySummary 区域 + 轮询）
    status: completed
    dependencies:
      - approval-model-dao
  - id: replay-config
    content: 创建 ReplayConfig.java + application yml 添加 replay 配置块
    status: completed
  - id: rbac-sidebar
    content: RBAC 权限 migration（V004）+ routes.tsx 侧边栏新增"流量回放"菜单项
    status: completed
  - id: phase3-verify
    content: Phase 3 编译验证（mvn compile + pnpm build）
    status: completed
    dependencies:
      - i18n-replay
      - approval-service-extend
      - release-page-replay
      - replay-config
      - rbac-sidebar
---

## 产品概述

完成流量回放功能的剩余未实现部分，覆盖 Phase 2.6 (i18n) 和 Phase 3 全部（审批集成、权限、配置化），确保 spec 13 项需求全部落地。

## 核心功能

1. **i18n 补全**：zh-CN.json / en.json 添加 replay 相关翻译键（route.trafficReplay、project.trafficReplay、project.simulationTest 等）
2. **审批表 Migration**：approval 表新增 replay_task_id 字段
3. **Approval 模型+DAO 扩展**：Approval.java 添加 replayTaskId，ApprovalDao 的 MAPPER/insert/update 方法扩展
4. **ApprovalService 扩展**：注入 ReplayService，submit() 中并行触发 autotest + replay，默认参数（24h/随机/10000/segment），两线程独立完成后状态流转 TESTING→PENDING
5. **审批详情页 replay 状态**：ReleaseListPage 抽屉中 testSummary 旁新增 replaySummary 区域，展示回放状态/结果/跳转，running 状态 5 秒轮询
6. **RBAC 权限 Migration**：新增 menu:replay、api:POST:/api/replay/*、api:GET:/api/replay/* 权限项，分配 admin 角色
7. **配置化参数**：application-dev.yml / application-prod.yml 添加 replay 配置，新建 ReplayConfig.java（@ConfigurationProperties，缺失快速失败）
8. **侧边栏菜单**：routes.tsx 在 monitoring 菜单组下新增"流量回放"菜单项，受 menu:replay 权限控制

## 技术栈

- 后端：Java + Spring Boot + JdbcTemplate + Lombok（沿用现有模式）
- 前端：React + TypeScript + Ant Design + react-i18next
- 数据库：MySQL（migration）+ RBAC 权限表

## 实现方案

### 审批集成核心设计

在 `ApprovalService.submit()` 中将现有 `runAutoTestAsync()` 替换为 `runTestsAsync()`，内部用两个独立线程并行触发 autotest 和 replay。两线程共享一个 `AtomicInteger` 计数器，最后一个完成的线程负责将状态从 TESTING → PENDING。这确保：

- 两个任务互不依赖，任一失败不影响另一个
- 审批不阻塞，submit 立即返回
- 状态流转正确：两个任务都完成后才进入 PENDING

### ApprovalDAO 扩展策略

完全沿用现有的 JdbcTemplate + RowMapper + GeneratedKeyHolder 模式：

- APPROVAL_MAPPER 添加 `.replayTaskId(...)` 映射
- insertApproval SQL 添加 `replay_task_id` 列
- 新增 `updateReplayTaskId(approvalId, replayTaskId)` 和 `updateAutotestRunId(approvalId, testRunId)` 方法
- 重构 `updateTestResult` 为更通用的方法

### 前端审批详情页扩展

在 ReleaseListPage 的 diffDrawer 中 testSummary 区域旁新增 replaySummary 区域。需先在 ApprovalVO 中添加 `replayTaskId` 和 `replaySummary` 字段，然后在 ApprovalController 的 detail 接口中填充 replay 信息。running 状态用 setInterval 5 秒轮询。

### 配置化参数

ReplayConfig 使用 `@ConfigurationProperties(prefix = "replay")` + `@Validated`，不设默认值，缺失时 Spring Boot 启动失败。在 ReplayService 中注入 ReplayConfig 替代硬编码参数。

### RBAC 权限

沿用 deploy/init.sql 中 rbac_permission 表的 ID 分配模式。新权限项 ID 从 40 开始，避免与现有 ID 冲突。

## 目录结构

```
ruleuler/
├── deploy/migrations/
│   ├── V20260419_003__approval_replay_task_id.sql       # [NEW] approval 表新增 replay_task_id 字段
│   └── V20260419_004__replay_permissions.sql            # [NEW] RBAC 权限项 + admin 角色分配
├── ruleuler-server/src/main/java/com/caritasem/ruleuler/server/
│   ├── approval/model/Approval.java                     # [MODIFY] 添加 replayTaskId 字段
│   ├── approval/ApprovalDao.java                        # [MODIFY] MAPPER/insert/update 方法扩展 replayTaskId
│   ├── approval/ApprovalService.java                    # [MODIFY] 注入 ReplayService，submit() 并行触发 autotest+replay
│   └── replay/
│       └── ReplayConfig.java                            # [NEW] @ConfigurationProperties(prefix="replay")，缺失快速失败
├── ruleuler-server/src/main/resources/
│   ├── application-dev.yml                              # [MODIFY] 添加 replay 配置块
│   └── application-prod.yml                             # [MODIFY] 添加 replay 配置块（环境变量引用）
├── ruleuler-admin/src/
│   ├── api/approval.ts                                  # [MODIFY] ApprovalVO 添加 replayTaskId/replaySummary
│   ├── i18n/locales/zh-CN.json                          # [MODIFY] 添加 replay 相关翻译键
│   ├── i18n/locales/en.json                             # [MODIFY] 添加 replay 相关翻译键
│   ├── pages/release/ReleaseListPage.tsx                # [MODIFY] 审批详情抽屉添加 replaySummary 区域
│   └── routes.tsx                                       # [MODIFY] monitoring 菜单组下新增"流量回放"菜单项
```

## 实现注意

- ApprovalService 的 runTestsAsync 中两个线程共享状态计数，用 AtomicInteger + synchronized 或 AtomicBoolean 确保 TESTING→PENDING 只执行一次
- 前端轮询使用 useEffect + setInterval，组件卸载或状态非 running 时清理 interval
- RBAC migration 的 permission INSERT 用 INSERT IGNORE 避免重复执行报错
- application-prod.yml 中 replay 配置项用环境变量引用（如 ${REPLAY_MAX_SAMPLE_SIZE:10000}），dev 直接写值
- ReplayConfig 缺失配置时启动失败：用 `@ConfigurationProperties` + spring-boot 配置校验，不设 fallback 默认值