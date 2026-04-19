---
name: fix-i18n-traffic-replay
overview: zh-CN.json 的 route 命名空间缺少 trafficReplay 键，导致菜单显示原始 key "route.trafficReplay"
todos:
  - id: fix-zh-i18n
    content: zh-CN.json route 对象添加 trafficReplay 键
    status: completed
---

## 问题

侧边栏菜单显示 `route.trafficReplay` 原文而非翻译文本"流量回放"

## 原因

`zh-CN.json` 的 `route` 对象里没有 `trafficReplay` 键，i18n 找不到翻译就回退显示 key 本身

## 修改

- `zh-CN.json`：route 对象添加 `"trafficReplay": "流量回放"`
- `en.json`：已正确，无需改

## 修改文件

### [MODIFY] ruleuler-admin/src/i18n/locales/zh-CN.json

- route 对象内添加 `"trafficReplay": "流量回放"`，放在 `auditLog` 之后