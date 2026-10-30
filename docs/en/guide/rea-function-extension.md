# Custom Functions

Write Java methods using the official template, package them into a JAR, and upload it to `ruleuler-server`. Standalone `ruleuler-client` instances pull the JAR to disk and load it into the classpath at startup. The server never executes user code.

```
Write methods from template → mvn package → Upload JAR
```

Do not add `ruleuler-client` as a dependency of your function JAR, do not manually copy JARs into `lib/`, and do not rebuild this repository to add functions.

## Three Steps

1. Copy `templates/ruleuler-function/` and implement your stateless methods following the template class.
2. Run `mvn package` and upload the generated JAR (via the Admin Console or the `curl` command in the template — both call the same endpoint).
3. Publish the knowledge package that references the action library. Restart `ruleuler-client` so the new JAR is loaded into the classpath.

REA call syntax: `GEOFUNCTIONS.DISTANCEKM(...)` (both bean ID and method name in uppercase).

## Startup & Version Updates

| Stage | Behavior |
|-------|----------|
| Initial Deployment | Pulls JARs to disk early in startup according to `ruleuler.projects` / `RULEULER_PROJECTS`. If `PropertiesLauncher` has already expanded the classpath, the process re-launches itself once with the same arguments |
| Updating & Publishing Functions at Runtime | Writes the new JAR to disk. **If neither push nor pull has all required JARs ready, the new package is rejected** and the existing package continues running. **Restarting `ruleuler-client` is required** to load updated classes |
| Encountering New Dependencies at Runtime | Downloads synchronously, then rejects the new package until the client restarts |

Hot-reload never swaps loaded Java classes in-place; `loader.path` and Spring auto-configuration are startup-time mechanisms.

Startup pre-pull requires configuring `RULEULER_PROJECTS` (both `./start.sh` and `docker compose` default to `RULEULER_PROJECTS=airport_gate_allocation_db`).

## Permissions

Uploading custom functions distributes executable bytecode to all `ruleuler-client` instances serving that project. Access is controlled per project (`project:function:upload` / `project:function:view`) and recorded in audit logs.

Client pull endpoints are protected by client↔server IP whitelist authentication.

## Constraints

| Item | Rule |
|------|------|
| Execution Location | Standalone `ruleuler-client`, never `ruleuler-server` |
| Statelessness | Methods must be thread-safe for concurrent execution |
| Parameter Types | `String / Integer / Long / Double / BigDecimal / Boolean / Date / List / Map`. `Object` and custom POJOs are prohibited. `Date` = `java.util.Date` |
| Return Types | `void` or any type from the parameter table above |
| Method Overloading | Prohibited; duplicate method names fail at compile time |
| Gray Release Conditions | Custom functions cannot be called inside gray routing condition expressions |
| `function-package` | Must match `[a-zA-Z0-9_-]+`, stored on disk as `<function-package>.jar` |

## Common Pitfalls

| Pitfall | Fact |
|---------|------|
| Replacing a JAR | Requires restarting `ruleuler-client` to enter the classpath; package refresh only writes to disk and rejects the new package until restart |
| Initial Startup | `start.sh` / Docker Compose pre-pulls the sample project by default; no pre-pull occurs if `RULEULER_PROJECTS` is unset |
| Changing Signatures / Deleting Methods | Re-save and re-publish all rules referencing the method |
| Rollback | Historical JAR versions are retained in the database; rolling back a release points dependencies back to the previous version without re-uploading the JAR |

## Template Upload

The template `pom.xml` uses `exec-maven-plugin` + `curl` to call `POST /api/projects/{project}/function-jars` with a bearer token.

