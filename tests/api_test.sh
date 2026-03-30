#!/bin/bash
# Ruleuler API 测试脚本
# 依赖: curl, jq
# 用法: bash tests/api_test.sh [base_url]
#
# 测试流程:
#   1. setup: 创建测试项目(jcr+db) 或 导入 fixtures/*.bak
#   2. test:  逐个验证 API
#   3. teardown: 删除测试项目

set -uo pipefail

BASE_URL="${1:-http://127.0.0.1:16009}"
FRAME_URL="$BASE_URL/urule/frame"
FIXTURE_DIR="$(dirname "$0")/fixtures"

# 颜色
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

PASS=0
FAIL=0
SKIP=0

# --- 工具函数 ---

log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; ((PASS++)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $1: $2"; ((FAIL++)); }
log_skip() { echo -e "${YELLOW}[SKIP]${NC} $1"; ((SKIP++)); }

# POST 请求，返回 body
post() {
    local url="$1"; shift
    curl -s -X POST "$url" "$@"
}

# POST 并断言返回 JSON 包含某个 key
assert_json_has() {
    local test_name="$1"
    local url="$2"
    local key="$3"
    shift 3
    local resp
    resp=$(post "$url" "$@")
    if echo "$resp" | jq -e ".$key" > /dev/null 2>&1; then
        log_pass "$test_name"
    else
        log_fail "$test_name" "missing key '$key' in: $resp"
    fi
}

# POST 并断言 HTTP status
assert_status() {
    local test_name="$1"
    local expected="$2"
    local url="$3"
    shift 3
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$url" "$@")
    if [ "$status" = "$expected" ]; then
        log_pass "$test_name"
    else
        log_fail "$test_name" "expected $expected, got $status"
    fi
}

# --- Setup ---

setup() {
    echo "=== Setup ==="

    # 创建 JCR 测试项目
    echo "Creating test project (jcr): test_project_fixture_001_jcr ..."
    post "$FRAME_URL/createProject" -d "newProjectName=test_project_fixture_001_jcr&storageType=jcr" > /dev/null

    # 创建 DB 测试项目
    echo "Creating test project (db): test_project_fixture_001_db ..."
    post "$FRAME_URL/createProject" -d "newProjectName=test_project_fixture_001_db&storageType=db" > /dev/null

    # 如果有 fixture bak 文件，导入
    if [ -d "$FIXTURE_DIR" ]; then
        for bak in "$FIXTURE_DIR"/*.bak; do
            [ -f "$bak" ] || continue
            echo "Importing fixture: $(basename "$bak") ..."
            curl -s -X POST "$FRAME_URL/importProject" \
                -F "file=@$bak" \
                -F "overwriteProject=true" > /dev/null
        done
    fi

    echo ""
}

# --- Teardown ---

teardown() {
    echo ""
    echo "=== Teardown ==="
    for proj in test_project_fixture_001_jcr test_project_fixture_001_db; do
        echo "Deleting project: $proj ..."
        post "$FRAME_URL/deleteFile" -d "path=/$proj&isFolder=true" > /dev/null 2>&1 || true
    done
}

# --- 测试用例 ---

test_load_projects() {
    assert_json_has "loadProjects" "$FRAME_URL/loadProjects" "repo"
}

test_project_exist_check() {
    # 已存在的项目
    local resp
    resp=$(post "$FRAME_URL/projectExistCheck" -d "newProjectName=test_project_fixture_001_jcr")
    local valid
    valid=$(echo "$resp" | jq -r '.valid')
    if [ "$valid" = "false" ]; then
        log_pass "projectExistCheck (exists)"
    else
        log_fail "projectExistCheck (exists)" "expected valid=false, got $valid"
    fi

    # 不存在的项目
    resp=$(post "$FRAME_URL/projectExistCheck" -d "newProjectName=_nonexistent_project_xyz")
    valid=$(echo "$resp" | jq -r '.valid')
    if [ "$valid" = "true" ]; then
        log_pass "projectExistCheck (not exists)"
    else
        log_fail "projectExistCheck (not exists)" "expected valid=true, got $valid"
    fi
}

test_create_file() {
    local resp
    resp=$(post "$FRAME_URL/createFile" -d "path=/test_project_fixture_001_jcr/testvars.vl.xml&type=vl.xml")
    if echo "$resp" | jq -e '.fullPath' > /dev/null 2>&1; then
        log_pass "createFile (VariableLibrary in jcr)"
    else
        log_fail "createFile (VariableLibrary in jcr)" "$resp"
    fi

    resp=$(post "$FRAME_URL/createFile" -d "path=/test_project_fixture_001_db/testdt.dt.xml&type=dt.xml")
    if echo "$resp" | jq -e '.fullPath' > /dev/null 2>&1; then
        log_pass "createFile (DecisionTable in db)"
    else
        log_fail "createFile (DecisionTable in db)" "$resp"
    fi
}

test_file_exist_check() {
    local resp valid
    resp=$(post "$FRAME_URL/fileExistCheck" -d "fullFileName=/test_project_fixture_001_jcr/testvars.vl.xml")
    valid=$(echo "$resp" | jq -r '.valid')
    if [ "$valid" = "false" ]; then
        log_pass "fileExistCheck (exists)"
    else
        log_fail "fileExistCheck (exists)" "expected valid=false, got $valid"
    fi
}

test_file_source() {
    local resp
    resp=$(post "$FRAME_URL/fileSource" -d "path=/test_project_fixture_001_jcr/testvars.vl.xml")
    if echo "$resp" | jq -e '.content' > /dev/null 2>&1; then
        log_pass "fileSource"
    else
        log_fail "fileSource" "$resp"
    fi
}

test_create_folder() {
    assert_json_has "createFolder" "$FRAME_URL/createFolder" "repo" -d "fullFolderName=/test_project_fixture_001_jcr/subfolder"
}

test_copy_file() {
    assert_json_has "copyFile" "$FRAME_URL/copyFile" "repo" \
        -d "oldFullPath=/test_project_fixture_001_jcr/testvars.vl.xml&newFullPath=/test_project_fixture_001_jcr/testvars_copy.vl.xml"
}

test_file_rename() {
    assert_json_has "fileRename" "$FRAME_URL/fileRename" "repo" \
        -d "path=/test_project_fixture_001_jcr/testvars_copy.vl.xml&newPath=/test_project_fixture_001_jcr/testvars_renamed.vl.xml"
}

test_delete_file() {
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FRAME_URL/deleteFile" \
        -d "path=/test_project_fixture_001_jcr/testvars_renamed.vl.xml")
    if [ "$status" = "200" ]; then
        log_pass "deleteFile"
    else
        log_fail "deleteFile" "status=$status"
    fi
}

test_export_project() {
    local status
    status=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FRAME_URL/exportProjectBackupFile" \
        -d "path=/test_project_fixture_001_jcr")
    if [ "$status" = "200" ]; then
        log_pass "exportProjectBackupFile"
    else
        log_fail "exportProjectBackupFile" "status=$status"
    fi
}

test_health() {
    local resp
    resp=$(curl -s "$BASE_URL/health")
    if echo "$resp" | jq -e '.status' > /dev/null 2>&1; then
        log_pass "GET /health"
    else
        log_fail "GET /health" "$resp"
    fi
}

# --- 导出测试项目为 fixture ---

export_fixture() {
    local project="$1"
    mkdir -p "$FIXTURE_DIR"
    local outfile="$FIXTURE_DIR/${project}.bak"
    echo "Exporting $project -> $outfile ..."
    curl -s -X POST "$FRAME_URL/exportProjectBackupFile" \
        -d "path=/$project" -o "$outfile"
    echo "Done. Size: $(wc -c < "$outfile") bytes"
}

# --- Main ---

if [ "${1:-}" = "export" ]; then
    # 用法: bash tests/api_test.sh export <project_name> [base_url]
    BASE_URL="${3:-http://127.0.0.1:16009}"
    FRAME_URL="$BASE_URL/urule/frame"
    export_fixture "${2:?Usage: api_test.sh export <project_name> [base_url]}"
    exit 0
fi

trap teardown EXIT

setup

echo "=== Tests ==="
test_health
test_load_projects
test_project_exist_check
test_create_file
test_file_exist_check
test_file_source
test_create_folder
test_copy_file
test_file_rename
test_delete_file
test_export_project

echo ""
echo "=== Results ==="
echo -e "${GREEN}PASS: $PASS${NC}  ${RED}FAIL: $FAIL${NC}  ${YELLOW}SKIP: $SKIP${NC}"

if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
