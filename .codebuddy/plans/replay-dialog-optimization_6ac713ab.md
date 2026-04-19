---
name: replay-dialog-optimization
overview: 优化知识包页面流量回放入口：点击"流量回放"按钮时，在 PackageEditor.jsx 当前页面弹框让用户选择必选项并创建回放任务，而非跳转到新页面重新选择知识包。同时清理 ReplayPage 中多余的误差配置项。
todos:
  - id: replay-dialog
    content: 创建 ReplayDialog.jsx 并修改 PackageEditor.jsx 按钮为弹框触发
    status: completed
  - id: replay-page-cleanup
    content: 清理 ReplayPage.tsx 误差配置和默认值，清理 replay.ts API 类型
    status: completed
  - id: build-verify
    content: 构建 console-js bundle 和 admin 前端，验证编译通过
    status: completed
    dependencies:
      - replay-dialog
      - replay-page-cleanup
---

## 用户需求

知识包页面（PackageEditor.jsx）点击"流量回放"按钮，不再跳转到新页面，而是在当前页面弹框，直接创建回放任务。

## 弹框表单字段

- 知识包：已选中，只读
- 时间范围：默认过去一周，可改
- 采样方式：默认全量，可选全量/随机/均匀
- 采样数量：默认10000，可调
- 缺失key填充策略：默认区间填充(segment)，可选不填充(null)/跳过(skip)/区间填充(segment)，标注为"缺失key的填充策略"
- 不要误差容差配置

## 附带清理

ReplayPage.tsx 删除误差容差配置，调整默认值与弹框一致

## 修改文件清单

### [MODIFY] ruleuler-console-js/src/package/components/PackageEditor.jsx

- 流量回放按钮 onClick：从 `window.top.location.href=...` 改为 `event.eventEmitter.emit(event.OPEN_REPLAY_DIALOG, {pkgId, pkgName, project})`
- render 中添加 `<ReplayDialog project={project} />`

### [NEW] ruleuler-console-js/src/package/components/ReplayDialog.jsx

- 遵循项目现有 CommonDialog + event 模式（参考 BatchTestDialog.jsx）
- componentDidMount 监听 OPEN_REPLAY_DIALOG，设置 state 并 modal('show')
- 表单：知识包只读、时间范围select(1天/3天/1周/1月，默认1周)、采样方式select(全量/随机/均匀，默认全量)、采样数量input(默认10000)、缺失key填充策略select(不填充/跳过/区间填充，默认区间填充)
- 点击"创建"：直接 fetch POST /api/replay/tasks，成功后 bootbox.confirm 问是否跳转查看

### [MODIFY] ruleuler-admin/src/pages/replay/ReplayPage.tsx

- 删除 toleranceMode/toleranceValue 状态和相关 UI
- 默认值调整：时间范围过去一周、采样策略默认all、缺失策略默认segment
- handleCreate 中不再传 toleranceConfig

### [MODIFY] ruleuler-admin/src/api/replay.ts

- createReplayTask 参数中删除 toleranceConfig