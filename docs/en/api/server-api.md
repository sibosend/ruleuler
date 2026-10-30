# Server API

> Base URL: `http://{host}:{port}` Default port: 16009

Legacy console file management APIs accept `POST` requests with form-encoded parameters, alongside RESTful `/api/*` endpoints.

## Project Management

| API | Parameters | Description |
|-----|-----------|-------------|
| `createProject` | `newProjectName`, `storageType` (`jcr`/`db`) | Create a project |
| `loadProjects` | `projectName` (optional), `classify` (optional), `types` (optional) | Load project list |
| `projectExistCheck` | `newProjectName` | Check whether a project exists |
| `deleteFile` | `path` (e.g. `/<projectName>`), `isFolder=true` | Delete a project |
| `importProject` | `file` (multipart), `overwriteProject` (optional) | Import a project backup |
| `exportProjectBackupFile` | `path` (e.g. `/<projectName>`) | Export a project backup |

## File Operations

| API | Parameters | Description |
|-----|-----------|-------------|
| `createFile` | `path`, `type` | Create a rule file |
| `createFolder` | `fullFolderName` | Create a folder |
| `copyFile` | `oldFullPath`, `newFullPath` | Copy a file |
| `deleteFile` | `path`, `isFolder` (optional) | Delete a file or folder |
| `fileRename` | `path`, `newPath` | Rename a file |
| `fileExistCheck` | `fullFileName` | Check whether a file exists |
| `fileSource` | `path` | View raw XML source of a file |
| `lockFile` / `unlockFile` | `file` | Lock or unlock a file |

### File Types

| Type Code | Description |
|-----------|-------------|
| `vl.xml` | Variable Library |
| `cl.xml` | Constant Library |
| `pl.xml` | Parameter Library |
| `al.xml` | Action Library |
| `rs.xml` | Rule Set |
| `dt.xml` | Decision Table |
| `dts.xml` | Script Decision Table |
| `dtree.xml` | Decision Tree |
| `rl.xml` | Decision Flow |
| `sc` | Scorecard |

## Health Check

`GET /health`

Returns `ip`, `port`, and `status`.

