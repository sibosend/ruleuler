# 管理端 API

> Base URL: `http://{host}:{port}` 默认端口: 16009

所有管理 API 均为 POST 请求，参数通过 form 表单提交。

## 项目管理

| API | 参数 | 说明 |
|-----|------|------|
| `createProject` | `newProjectName`, `storageType`(`jcr`/`db`) | 创建项目 |
| `loadProjects` | `projectName`(可选), `classify`(可选), `types`(可选) | 加载项目列表 |
| `projectExistCheck` | `newProjectName` | 检查项目是否存在 |
| `deleteFile` | `path`(如 `/<项目名>`), `isFolder=true` | 删除项目 |
| `importProject` | `file`(multipart), `overwriteProject`(可选) | 导入项目备份 |
| `exportProjectBackupFile` | `path`(如 `/<项目名>`) | 导出项目备份 |

## 文件操作

| API | 参数 | 说明 |
|-----|------|------|
| `createFile` | `path`, `type` | 创建规则文件 |
| `createFolder` | `fullFolderName` | 创建文件夹 |
| `copyFile` | `oldFullPath`, `newFullPath` | 复制文件 |
| `deleteFile` | `path`, `isFolder`(可选) | 删除文件/文件夹 |
| `fileRename` | `path`, `newPath` | 重命名文件 |
| `fileExistCheck` | `fullFileName` | 检查文件是否存在 |
| `fileSource` | `path` | 查看文件 XML 源码 |
| `lockFile` / `unlockFile` | `file` | 锁定/解锁文件 |

### 文件类型

| 类型代码 | 说明 |
|----------|------|
| `vl.xml` | 变量库 |
| `cl.xml` | 常量库 |
| `pl.xml` | 参数库 |
| `al.xml` | 动作库 |
| `rs.xml` | 规则集 |
| `dt.xml` | 决策表 |
| `dts.xml` | 脚本决策表 |
| `dtree.xml` | 决策树 |
| `rl.xml` | 决策流 |
| `sc` | 评分卡 |

## 健康检查

`GET /health`

返回 ip、port、status。
