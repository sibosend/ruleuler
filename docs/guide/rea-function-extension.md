# 自定义函数

按官方模板写几个方法，打一个 jar，上传给 server。独立 client 启动时落到盘上并加载。server 不执行用户代码。

```
按模板写方法 → mvn package → 上传一份 jar
```

不要把 `ruleuler-client` 引进函数包，不要拷 `lib/`，不要编本仓库。

## 三步

1. 复制 `templates/ruleuler-function/`，按里面的类写方法。
2. `mvn package`，把打出来的 jar 上传（管理后台或模板里的 curl，同一个接口）。
3. 发布引用了该动作库的知识包。重启 client 后新 jar 进 classpath。

REA 写法：`GEOFUNCTIONS.DISTANCEKM(...)`（bean / 方法全大写）。

## 启动与换版

| 时机 | 行为 |
|------|------|
| 首次部署 | 启动早期按 `ruleuler.projects` / `RULEULER_PROJECTS` 拉 jar 落盘。PropertiesLauncher 已展开 classpath 时，进程会**同参数再拉起一次**（用户看到一次 `java -jar`） |
| 运行中改函数并上传、发布 | 落盘新 jar。**push 和 pull 都不齐则拒载新包**，旧包继续跑。必须**重启 client** |
| 运行中第一次碰到新 deps | 同步下载，可能慢，然后拒新包 |

刷新绝不换 class。`loader.path` 和自动配置都是启动期语义。

启动预拉必须配置项目列表，否则不预拉（避免把别的项目 jar 拉下来）。`./start.sh` 和 docker compose 默认 `RULEULER_PROJECTS=airport_gate_allocation_db`，可覆盖。未配时靠首次执行 / 发布推送按 `packageId` 落盘，然后重启。

## 权限

上传自定义函数 = 向该项目全部 client 分发可执行字节码。按项目授权，并有审计。不要把这个权限随便给人。

拉取接口走既有 client↔server 服务端点认证（IP 白名单），不能公网裸奔。

## 约束

| 项 | 规则 |
|----|------|
| 执行位置 | 独立 client，不是 server |
| 无状态 | 方法必须可并发 |
| 参数类型 | `String / Integer / Long / Double / BigDecimal / Boolean / Date / List / Map`。禁 `Object`、禁 POJO。`Date` = `java.util.Date` |
| 返回值 | `void` 或上表（`Object` 仅返回擦除） |
| 重载 | 禁止，同名方法编译失败 |
| 灰度条件 | 条件里不能调自定义函数 |
| `function-package` | `[a-zA-Z0-9_-]+`，一包一份 version 文件，落盘名 `<function-package>.jar`（不带版本号） |

## 坑

| 坑 | 事实 |
|----|------|
| 换 jar | 重启 client 才进 classpath。刷新只落盘、拒载新包 |
| 首次 | `start.sh` / compose 默认预拉示例项目。没配 `RULEULER_PROJECTS` 则不预拉 |
| 运行中第一次碰到新 deps | 同步下载（可能慢），然后拒新包，等重启 |
| 改签名 / 删方法 | 重存并重发引用它的规则。版本对齐 ≠ API 兼容，只升 jar 会在 `classMatch` 才炸 |
| void 进条件 | 本期 REA 拦不住，写了运行期行为未定义 |
| 上传权限 | 等于向该项目全部 client 推代码 |
| 存量手写动作库 | 不校验、不炸，jar 仍要人拷 `lib/`。想走自动分发必须迁到模板 |
| 回滚 | 表留历史版本。发布时 deps 指回旧 version，不必重传 jar |

## 模板上传

模板 pom 用 `exec-maven-plugin` + `curl` 打 `POST /api/projects/{project}/function-jars`，带登录 token。不提供 maven 插件。
