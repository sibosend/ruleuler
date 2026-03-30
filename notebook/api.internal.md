# Ruleuler API 文档

> Base URL: `http://{host}:{port}`
> Server 默认端口: 16009 (dev), Client 默认端口: 9281 (dev)

---

## 一、Server 管理 API (`/urule/frame/*`)

所有管理 API 均为 POST 请求，参数通过 form 表单提交。

由 `StorageAwareFrameServletHandler` 覆盖的方法会标注 `[覆盖]`。

### 1. 项目管理

| API | 参数 | 说明 |
|-----|------|------|
| `createProject` [覆盖] | `newProjectName`, `storageType`(必填: `jcr`/`db`) | 创建项目，storageType 决定存储方式 |
| `loadProjects` | `projectName`(可选), `classify`(可选), `types`(可选: all/lib/rule/table/tree/flow), `searchFileName`(可选) | 加载项目列表。projectName 为空时加载全部 |
| `projectExistCheck` | `newProjectName` | 检查项目是否存在，返回 `{valid: true/false}` |
| `deleteFile` | `path`(如 `/<项目名>`), `isFolder=true` | 删除项目(文件夹) |
| `importProject` | `file`(multipart), `overwriteProject`(可选, 默认true) | 导入项目备份文件 |
| `exportProjectBackupFile` | `path`(如 `/<项目名>`) | 导出项目备份 .bak 文件 |

### 2. 文件操作

| API | 参数 | 说明 |
|-----|------|------|
| `createFile` | `path`, `type`(文件类型) | 创建规则文件 |
| `createFolder` | `fullFolderName` | 创建文件夹 |
| `copyFile` | `oldFullPath`, `newFullPath` | 复制文件 |
| `deleteFile` | `path`, `isFolder`(可选) | 删除文件/文件夹 |
| `fileRename` | `path`, `newPath` | 重命名文件 |
| `fileExistCheck` | `fullFileName` | 检查文件是否存在 |
| `fileSource` | `path` | 查看文件XML源码 |
| `fileVersions` | `path` | 获取文件版本列表 |
| `loadFileVersions` | `file` | 加载文件版本列表 |
| `lockFile` | `file` | 锁定文件 |
| `unlockFile` | `file` | 解锁文件 |

文件类型(`type`): `vl.xml`(变量库), `cl.xml`(常量库), `pl.xml`(参数库), `al.xml`(动作库), `rs.xml`(规则集), `ul`(UL脚本), `dt.xml`(决策表), `dts.xml`(脚本决策表), `dtree.xml`(决策树), `rl.xml`(决策流), `sc`(评分卡)

---

## 二、Server REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查，返回 ip/port/status |

---

## 三、Client REST API

| 方法 | 路径 | 参数 | 说明 |
|------|------|------|------|
| GET | `/health` | - | 健康检查 |
| POST | `/process/{project}/{knowledge}/{process}` | Body: 按变量类别分组的 JSON | 执行决策流 |
| POST | `/inspector/variables/{project}/{packageId}` | - | 提取知识包使用的变量列表 |

### 决策流调用示例

```bash
curl -X POST 'http://127.0.0.1:9281/process/airport_gate_allocation_db/gate_pkg/gate_allocation_flow' \
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

请求体按变量类别名分组，类别名需与知识包中定义的一致。clazz 由知识包自动解析，无需调用方指定。

返回示例：
```json
{
  "status": 200,
  "msg": "ok",
  "data": {
    "can_score": 80,
    "can_reason": "高峰时段大客流优先近机位",
    "GateResult": {
      "priority_score": 110,
      "reason": "中客流国际航班近机位",
      "assigned_gate": "B区-202",
      "gate_type": "近机位"
    }
  }
}
```

返回只包含参数输出和被规则引擎修改的变量字段，未变化的输入字段不返回。

### 变量提取示例

```bash
curl -X POST http://127.0.0.1:9281/inspector/variables/oneloan/ab_new_process
```

---

## 四、Console Servlet Handlers (`/urule/*`)

| Handler | 路径 | 说明 |
|---------|------|------|
| FrameServletHandler | `/urule/frame` | 核心管理(上述 API) |
| ConsoleServletHandler | `/urule/console` | 控制台页面 |
| VariableEditorServletHandler | `/urule/variableeditor` | 变量库编辑器 |
| ParameterEditorServletHandler | `/urule/parametereditor` | 参数库编辑器 |
| ConstantEditorServletHandler | `/urule/constanteditor` | 常量库编辑器 |
| ActionEditorServletHandler | `/urule/actioneditor` | 动作库编辑器 |
| RuleSetEditorServletHandler | `/urule/ruleseteditor` | 规则集编辑器 |
| DecisionTableEditorServletHandler | `/urule/decisiontableeditor` | 决策表编辑器 |
| ScriptDecisionTableEditorServletHandler | `/urule/scriptdecisiontableeditor` | 脚本决策表编辑器 |
| DecisionTreeEditorServletHandler | `/urule/decisiontreeeditor` | 决策树编辑器 |
| ScorecardEditorServletHandler | `/urule/scorecardeditor` | 评分卡编辑器 |
| ULEditorServletHandler | `/urule/uleditor` | UL脚本编辑器 |
| RuleFlowDesignerServletHandler | `/urule/ruleflowdesigner` | 决策流设计器 |
| PackageEditorServletHandler | `/urule/packageeditor` | 知识包编辑器 |
| ClientConfigServletHandler | `/urule/clientconfig` | 客户端配置 |
| PermissionConfigServletHandler | `/urule/permissionconfig` | 权限配置 |
| XmlServletHandler | `/urule/xml` | XML处理 |
| CommonServletHandler | `/urule/common` | 通用操作 |
| LoadKnowledgeServletHandler | `/urule/loadknowledge` | 加载知识包 |
| ReteDiagramServletHandler | `/urule/retediagram` | Rete网络图 |
| ResourceLoaderServletHandler | `/urule/res` | 静态资源加载 |
