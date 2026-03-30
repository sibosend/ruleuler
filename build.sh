#!/bin/bash
# 构建打包脚本
# 功能：
#   1. 从 git tag 读取上一个版本号，自动升级版本
#   2. 使用新版本号打包 Maven 项目（通用包，不绑定环境）
#   3. 生成 dist/version.json 供 push.dist.sh 使用
# 
# 用法: ./build.sh [bump_type]
#   ./build.sh              # patch+1 (0.1.2 -> 0.1.3)
#   ./build.sh minor        # minor+1 (0.1.3 -> 0.2.0)
#   ./build.sh major        # major+1 (0.2.0 -> 1.0.0)

set -e
cd "$(dirname "$0")"

export JAVA_HOME="$(jenv javahome)"

BUMP_TYPE=${1:-patch}
DIST_DIR="dist"

# 从git tag读取上一个版本号
LAST_TAG=$(git tag -l "v*" --sort=-v:refname | head -1 | sed 's/^v//')
if [[ -z "$LAST_TAG" ]]; then
    CURRENT_VERSION="0.0.0"
else
    CURRENT_VERSION=$LAST_TAG
fi

# 根据类型升级版本号
IFS='.' read -r major minor patch <<< "$CURRENT_VERSION"
case $BUMP_TYPE in
    major)
        NEW_VERSION="$((major + 1)).0.0"
        ;;
    minor)
        NEW_VERSION="${major}.$((minor + 1)).0"
        ;;
    *)  # patch
        NEW_VERSION="${major}.${minor}.$((patch + 1))"
        ;;
esac

echo "=== 升级版本: ${CURRENT_VERSION} -> ${NEW_VERSION} (${BUMP_TYPE}) ==="

mkdir -p ${DIST_DIR}/server ${DIST_DIR}/client

# 1. Maven 构建（用 prod profile，application.yml 中 @profileActive@ 替换为 prod）
mvn clean package -Pprod -Dmaven.test.skip=true -Drevision=${NEW_VERSION}

# 2. 前端构建
echo "=== 构建 ruleuler-admin ==="
cd ruleuler-admin
pnpm install --frozen-lockfile
pnpm build
cd ..

# 3. 拷贝制品到dist
echo "=== 拷贝制品 ==="

# Server
cp ruleuler-server/target/ruleuler-server-*.jar ${DIST_DIR}/server/app.jar
rm -rf ${DIST_DIR}/server/lib
cp -r ruleuler-server/target/lib ${DIST_DIR}/server/

# Client
cp ruleuler-client/target/ruleuler-client-*.jar ${DIST_DIR}/client/app.jar
rm -rf ${DIST_DIR}/client/lib
cp -r ruleuler-client/target/lib ${DIST_DIR}/client/

# Admin 前端
cp -r ruleuler-admin/dist ${DIST_DIR}/admin

# 4. 生成版本信息
cat > ${DIST_DIR}/version.json << EOF
{
  "version": "${NEW_VERSION}",
  "buildTime": "$(date '+%Y-%m-%d %H:%M:%S')",
  "gitCommit": "$(git rev-parse --short HEAD)",
  "gitBranch": "$(git rev-parse --abbrev-ref HEAD)",
  "libChecksum": "$(find ${DIST_DIR}/server/lib -name '*.jar' -exec md5 -q {} \; 2>/dev/null | sort | md5 -q || find ${DIST_DIR}/server/lib -name '*.jar' -exec md5sum {} \; | sort | md5sum | cut -d' ' -f1)"
}
EOF

echo ""
echo "=== 制品信息 ==="
cat ${DIST_DIR}/version.json
ls -lh ${DIST_DIR}/server/app.jar
du -sh ${DIST_DIR}/server/lib

echo ""
echo "=== 构建完成! ==="
echo ""
echo "本地 dev 启动:"
echo "  ./start.sh dev"
echo ""
echo "Prod 部署 (supervisor 管理，需设置环境变量):"
echo "  DB_URL / DB_USERNAME / DB_PASSWORD"
echo "  AUTH_JWT_SECRET / AUTH_JWT_EXPIRATION_HOURS"
echo "  ADMIN_BASE_URL / URULE_REPO_DIR / SERVER_URL"
echo ""
echo "发布版本:"
echo "  ./push.dist.sh                 # 发布版本 ${NEW_VERSION}"
echo "  ./push.dist.sh \"fix: 修复bug\"  # 发布版本并添加说明"

