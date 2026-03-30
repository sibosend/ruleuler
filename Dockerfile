# 全量构建：编译环境 + 运行环境
# 利用 Docker 层缓存：依赖层独立，源码变更只重编译
#
# 用法: docker compose up -d --build

# ── 阶段1: 前端构建 ──────────────────────────────────────────
FROM node:20-slim AS frontend

RUN corepack enable && corepack prepare pnpm@9 --activate

WORKDIR /build

# console-js: 先装依赖（缓存层）
COPY ruleuler-console-js/package.json ruleuler-console-js/pnpm-lock.yaml ruleuler-console-js/
RUN cd ruleuler-console-js && pnpm install --frozen-lockfile

# console-js: 拷源码 + 构建（webpack 输出到 ../ruleuler-console/...）
COPY ruleuler-console-js ruleuler-console-js
COPY ruleuler-console/src/main/resources/urule-asserts ruleuler-console/src/main/resources/urule-asserts
RUN cd ruleuler-console-js && pnpm run start

# admin: 先装依赖（缓存层）
COPY ruleuler-admin/package.json ruleuler-admin/pnpm-lock.yaml ruleuler-admin/
RUN cd ruleuler-admin && pnpm install --frozen-lockfile

# admin: 拷源码 + 构建
COPY ruleuler-admin ruleuler-admin
COPY docs docs
RUN cd ruleuler-admin && pnpm run build

# ── 阶段2: Maven 构建 ───────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS maven

WORKDIR /build

# 先拷所有 pom 下载依赖（缓存层）
COPY pom.xml .
COPY ruleuler-core/pom.xml ruleuler-core/
COPY ruleuler-console/pom.xml ruleuler-console/
COPY ruleuler-server/pom.xml ruleuler-server/
COPY ruleuler-client/pom.xml ruleuler-client/

# 拷源码
COPY ruleuler-core ruleuler-core
COPY ruleuler-console ruleuler-console
COPY ruleuler-server ruleuler-server
COPY ruleuler-client ruleuler-client

# console-js 构建产物覆盖进 console resources
COPY --from=frontend /build/ruleuler-console/src/main/resources/urule-asserts/js ruleuler-console/src/main/resources/urule-asserts/js

# 构建：先安装根 pom 到本地仓库（子模块 parent 解析需要），再全量构建
RUN mvn install -N -DskipTests && mvn clean install -Pprod -DskipTests -q

# ── 阶段3: Server 运行镜像 ──────────────────────────────────
FROM eclipse-temurin:21-jre AS server

WORKDIR /app
COPY --from=maven /build/ruleuler-server/target/ruleuler-server-*.jar app.jar
COPY --from=maven /build/ruleuler-server/target/lib lib/
COPY --from=frontend /build/ruleuler-admin/dist admin-dist/
COPY --from=frontend /build/ruleuler-console/src/main/resources console-static/

EXPOSE 16009
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dloader.path=lib/ -Dadmin.staticPath=/app/admin-dist -Dconsole.staticPath=/app/console-static -Dlogging.file.path=/app/logs -jar app.jar"]

# ── 阶段4: Client 运行镜像 ──────────────────────────────────
FROM eclipse-temurin:21-jre AS client

WORKDIR /app
COPY --from=maven /build/ruleuler-client/target/ruleuler-client-*.jar app.jar
COPY --from=maven /build/ruleuler-client/target/lib lib/

EXPOSE 16001
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dloader.path=lib/ -Dlogging.file.path=/app/logs -jar app.jar"]
