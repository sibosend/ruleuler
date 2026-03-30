#!/bin/bash
# 构建机场停机坪分配测试项目，覆盖 jcr 和 db 两种存储
# 流程: 清空 -> 创建 -> 写入组件 -> 仿真测试 -> 清空
# 用法: bash tests/setup_fixture.sh [base_url]

set -uo pipefail

BASE_URL="${1:-http://127.0.0.1:16009}"
FRAME_URL="$BASE_URL/urule/frame"
COMMON_URL="$BASE_URL/urule/common"
PACKAGE_URL="$BASE_URL/urule/packageeditor"

GREEN='\033[0;32m'; RED='\033[0;31m'; NC='\033[0m'
PASS=0; FAIL=0
ok()   { echo -e "${GREEN}[OK]${NC} $1"; ((PASS++)); }
fail() { echo -e "${RED}[FAIL]${NC} $1: $2"; ((FAIL++)); }

delete_project() {
    curl -s -X POST "$FRAME_URL/deleteFile" -d "path=/$1&isFolder=true" > /dev/null 2>&1 || true
}

create_project() {
    local r
    r=$(curl -s -X POST "$FRAME_URL/createProject" -d "newProjectName=$1&storageType=$2")
    if echo "$r" | grep -q "fullPath"; then ok "创建项目 $1 ($2)"
    elif echo "$r" | grep -qi "exist\|Duplicate"; then ok "项目 $1 已存在，继续"
    else fail "创建项目$1($2)" "$r"; return 1; fi
}

put_file() {
    local proj="$1" path="$2" type="$3" content="$4" label="$5"
    curl -s -X POST "$FRAME_URL/createFile" -d "path=/${proj}/${path}&type=${type}" > /dev/null 2>&1
    curl -s -X POST "$COMMON_URL/saveFile" \
        -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
        --data-urlencode "file=/${proj}/${path}" \
        --data-urlencode "content=${content}" \
        --data-urlencode "newVersion=false" > /dev/null
    ok "$label"
}

extract_var() {
    echo "$1" | python3 -c "
import sys,json; d=json.load(sys.stdin)
for c in d['data']:
    if c['name']=='GateResult':
        for v in c['variables']:
            if v['name']=='$2': print(v.get('defaultValue',''))
" 2>/dev/null
}

# === 创建所有组件 ===
setup_components() {
    local P="$1" PFX="$2"  # PFX: jcr项目="jcr:", db项目=""

    put_file "$P" "flight_info.vl.xml" "vl.xml" '<?xml version="1.0" encoding="utf-8"?>
<variable-library>
  <category name="FlightInfo" clazz="com.test.FlightInfo" type="Custom">
    <var name="aircraft_type" label="机型" type="String" act="InOut"/>
    <var name="flight_number" label="航班号" type="String" act="InOut"/>
    <var name="passenger_count" label="旅客数" type="Integer" act="InOut"/>
    <var name="is_international" label="是否国际" type="Boolean" act="InOut"/>
    <var name="arrival_time" label="到达时间" type="Integer" act="InOut"/>
  </category>
</variable-library>' "[$P] 变量库-航班信息"

    put_file "$P" "gate_result.vl.xml" "vl.xml" '<?xml version="1.0" encoding="utf-8"?>
<variable-library>
  <category name="GateResult" clazz="com.test.GateResult" type="Custom">
    <var name="assigned_gate" label="分配停机位" type="String" act="InOut"/>
    <var name="gate_type" label="机位类型" type="String" act="InOut"/>
    <var name="priority_score" label="优先级评分" type="Integer" act="InOut"/>
    <var name="reason" label="分配原因" type="String" act="InOut"/>
  </category>
</variable-library>' "[$P] 变量库-停机坪结果"

    put_file "$P" "intl_priority.rs.xml" "rs.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<rule-set>
  <import-variable-library path=\"${PFX}/${P}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${P}/gate_result.vl.xml\"/>
  <rule name=\"国际航班近机位\" salience=\"10\">
    <if><and>
      <atom op=\"Equals\">
        <left var-category=\"FlightInfo\" var=\"is_international\" var-label=\"是否国际\" datatype=\"Boolean\"/>
        <value content=\"true\" type=\"Input\"/>
      </atom>
    </and></if>
    <then>
      <var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"近机位\" type=\"Input\"/></var-assign>
      <var-assign var-category=\"GateResult\" var=\"reason\" var-label=\"分配原因\" datatype=\"String\"><value content=\"国际航班优先分配近机位\" type=\"Input\"/></var-assign>
    </then>
  </rule>
  <rule name=\"国内航班默认\" salience=\"1\">
    <if><and>
      <atom op=\"Equals\">
        <left var-category=\"FlightInfo\" var=\"is_international\" var-label=\"是否国际\" datatype=\"Boolean\"/>
        <value content=\"false\" type=\"Input\"/>
      </atom>
    </and></if>
    <then>
      <var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"远机位\" type=\"Input\"/></var-assign>
      <var-assign var-category=\"GateResult\" var=\"reason\" var-label=\"分配原因\" datatype=\"String\"><value content=\"国内航班默认远机位\" type=\"Input\"/></var-assign>
    </then>
  </rule>
</rule-set>" "[$P] 规则集-国际航班优先级"

    put_file "$P" "gate_assign.dt.xml" "dt.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<decision-table>
  <import-variable-library path=\"${PFX}/${P}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${P}/gate_result.vl.xml\"/>
  <row num=\"0\" height=\"40\"/><row num=\"1\" height=\"40\"/><row num=\"2\" height=\"40\"/>
  <col num=\"0\" width=\"180\" type=\"Criteria\" var-category=\"FlightInfo\" var=\"aircraft_type\" datatype=\"String\" var-label=\"机型\"/>
  <col num=\"1\" width=\"180\" type=\"Assignment\" var-category=\"GateResult\" var=\"assigned_gate\" datatype=\"String\" var-label=\"停机位\"/>
  <cell row=\"0\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"B737\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"0\" col=\"1\" rowspan=\"1\"><value content=\"A区-101\" type=\"Input\"/></cell>
  <cell row=\"1\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"A320\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"1\" col=\"1\" rowspan=\"1\"><value content=\"A区-102\" type=\"Input\"/></cell>
  <cell row=\"2\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"B777\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"2\" col=\"1\" rowspan=\"1\"><value content=\"B区-201\" type=\"Input\"/></cell>
</decision-table>" "[$P] 决策表-机型停机坪分配"

    put_file "$P" "pax_routing.dtree.xml" "dtree.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<decision-tree>
  <import-variable-library path=\"${PFX}/${P}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${P}/gate_result.vl.xml\"/>
  <variable-tree-node>
    <left var-category=\"FlightInfo\" var=\"passenger_count\" var-label=\"旅客数\" datatype=\"Integer\"/>
    <condition-tree-node op=\"GreaterThen\"><value content=\"200\" type=\"Input\"/>
      <action-tree-node><var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"近机位\" type=\"Input\"/></var-assign></action-tree-node>
    </condition-tree-node>
    <condition-tree-node op=\"LessThenEquals\"><value content=\"200\" type=\"Input\"/>
      <action-tree-node><var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"远机位\" type=\"Input\"/></var-assign></action-tree-node>
    </condition-tree-node>
  </variable-tree-node>
</decision-tree>" "[$P] 决策树-客流量分配"

    put_file "$P" "priority_score.sc" "sc" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<scorecard scoring-type=\"sum\" assign-target-type=\"variable\"
  var-category=\"GateResult\" var=\"priority_score\" datatype=\"Integer\"
  attr-col-category=\"FlightInfo\" attr-col-name=\"评估属性\" attr-col-width=\"180\"
  condition-col-name=\"条件\" condition-col-width=\"180\"
  score-col-name=\"得分\" score-col-width=\"100\">
  <import-variable-library path=\"${PFX}/${P}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${P}/gate_result.vl.xml\"/>
  <attribute-row row-number=\"0\"><condition-row row-number=\"0\"/></attribute-row>
  <attribute-row row-number=\"1\"><condition-row row-number=\"1\"/></attribute-row>
  <card-cell row=\"0\" col=\"1\" type=\"attribute\" var=\"passenger_count\" var-label=\"旅客数\" datatype=\"Integer\"/>
  <card-cell row=\"0\" col=\"2\" type=\"condition\"><joint type=\"and\"><condition op=\"GreaterThen\"><value content=\"150\" type=\"Input\"/></condition></joint></card-cell>
  <card-cell row=\"0\" col=\"3\" type=\"score\"><value content=\"30\" type=\"Input\"/></card-cell>
  <card-cell row=\"1\" col=\"1\" type=\"attribute\" var=\"is_international\" var-label=\"是否国际\" datatype=\"Boolean\"/>
  <card-cell row=\"1\" col=\"2\" type=\"condition\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"true\" type=\"Input\"/></condition></joint></card-cell>
  <card-cell row=\"1\" col=\"3\" type=\"score\"><value content=\"50\" type=\"Input\"/></card-cell>
</scorecard>" "[$P] 评分卡-优先级评分"

    put_file "$P" "gate_allocation.rl.xml" "rl.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<rule-flow id=\"gate_allocation_flow\">
  <import-variable-library path=\"${PFX}/${P}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${P}/gate_result.vl.xml\"/>
  <start name=\"开始\" x=\"40\" y=\"200\" width=\"50\" height=\"50\">
    <connection g=\"\" to=\"国际航班规则\"/>
  </start>
  <rule name=\"国际航班规则\" file=\"${PFX}/${P}/intl_priority.rs.xml\" version=\"LATEST\" x=\"160\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"机型分配\"/>
  </rule>
  <rule name=\"机型分配\" file=\"${PFX}/${P}/gate_assign.dt.xml\" version=\"LATEST\" x=\"340\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"客流量判断\"/>
  </rule>
  <rule name=\"客流量判断\" file=\"${PFX}/${P}/pax_routing.dtree.xml\" version=\"LATEST\" x=\"520\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"优先级评分\"/>
  </rule>
  <rule name=\"优先级评分\" file=\"${PFX}/${P}/priority_score.sc\" version=\"LATEST\" x=\"700\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"结束\"/>
  </rule>
  <end name=\"结束\" x=\"880\" y=\"200\" width=\"50\" height=\"50\"/>
</rule-flow>" "[$P] 决策流-停机坪分配主流程"
}

# === 仿真测试 ===
run_flow_test() {
    local P="$1" PFX="$2"
    local COOKIE="/tmp/ruleuler_test_${P}.txt"
    local FLOW_FILE="${PFX}/${P}/gate_allocation.rl.xml"
    local FLOW_ID="gate_allocation_flow"

    # 构建知识库
    local r
    r=$(curl -s -X POST "$PACKAGE_URL/loadForTestVariableCategories" \
        -b "$COOKIE" -c "$COOKIE" --data-urlencode "files=${FLOW_FILE}")
    echo "$r" | grep -q "FlightInfo" || { fail "[$P] 构建知识库" "$r"; rm -f "$COOKIE"; return; }
    ok "[$P] 构建知识库"

    # 用例1: 国际航班 B777 250人
    local DATA1='[{"name":"FlightInfo","clazz":"com.test.FlightInfo","variables":[
      {"name":"aircraft_type","type":"String","defaultValue":"B777"},
      {"name":"flight_number","type":"String","defaultValue":"CA001"},
      {"name":"passenger_count","type":"Integer","defaultValue":"250"},
      {"name":"is_international","type":"Boolean","defaultValue":"true"},
      {"name":"arrival_time","type":"Integer","defaultValue":"800"}
    ]},{"name":"GateResult","clazz":"com.test.GateResult","variables":[
      {"name":"assigned_gate","type":"String"},{"name":"gate_type","type":"String"},
      {"name":"priority_score","type":"Integer"},{"name":"reason","type":"String"}
    ]}]'

    r=$(curl -s -X POST "$PACKAGE_URL/doTest" -b "$COOKIE" -c "$COOKIE" \
        -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
        --data-urlencode "data=${DATA1}" --data-urlencode "flowId=${FLOW_ID}" \
        --data-urlencode "files=${FLOW_FILE}")

    if echo "$r" | grep -q "info"; then
        local gt; gt=$(extract_var "$r" "gate_type")
        local ag; ag=$(extract_var "$r" "assigned_gate")
        [ "$gt" = "近机位" ] && ok "[$P] 国际B777->近机位" || fail "[$P] gate_type" "期望近机位,实际$gt"
        [ "$ag" = "B区-201" ] && ok "[$P] B777->B区-201" || fail "[$P] assigned_gate" "期望B区-201,实际$ag"
    else
        fail "[$P] 决策流执行(国际)" "$r"
    fi

    # 用例2: 国内航班 A320 100人
    local DATA2='[{"name":"FlightInfo","clazz":"com.test.FlightInfo","variables":[
      {"name":"aircraft_type","type":"String","defaultValue":"A320"},
      {"name":"flight_number","type":"String","defaultValue":"MU5678"},
      {"name":"passenger_count","type":"Integer","defaultValue":"100"},
      {"name":"is_international","type":"Boolean","defaultValue":"false"},
      {"name":"arrival_time","type":"Integer","defaultValue":"1400"}
    ]},{"name":"GateResult","clazz":"com.test.GateResult","variables":[
      {"name":"assigned_gate","type":"String"},{"name":"gate_type","type":"String"},
      {"name":"priority_score","type":"Integer"},{"name":"reason","type":"String"}
    ]}]'

    r=$(curl -s -X POST "$PACKAGE_URL/doTest" -b "$COOKIE" -c "$COOKIE" \
        -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
        --data-urlencode "data=${DATA2}" --data-urlencode "flowId=${FLOW_ID}" \
        --data-urlencode "files=${FLOW_FILE}")

    if echo "$r" | grep -q "info"; then
        local gt2; gt2=$(extract_var "$r" "gate_type")
        local ag2; ag2=$(extract_var "$r" "assigned_gate")
        [ "$gt2" = "远机位" ] && ok "[$P] 国内A320->远机位" || fail "[$P] gate_type" "期望远机位,实际$gt2"
        [ "$ag2" = "A区-102" ] && ok "[$P] A320->A区-102" || fail "[$P] assigned_gate" "期望A区-102,实际$ag2"
    else
        fail "[$P] 决策流执行(国内)" "$r"
    fi

    rm -f "$COOKIE"
}

# === 主流程 ===

JCR_PROJECT="test_project_fixture_001"
DB_PROJECT="test_project_fixture_001_db"
# 注: 不管底层是 jcr 还是 db 存储，XML 引用路径统一用 jcr: 前缀
# jcr: 代表"从 repository 读取"，不是存储类型

echo "========================================="
echo " 清空测试项目"
echo "========================================="
delete_project "$JCR_PROJECT"
delete_project "$DB_PROJECT"
ok "清空完成"

echo ""
echo "========================================="
echo " JCR 存储测试"
echo "========================================="
create_project "$JCR_PROJECT" "jcr"
setup_components "$JCR_PROJECT" "jcr:"
run_flow_test "$JCR_PROJECT" "jcr:"

echo ""
echo "========================================="
echo " DB 存储测试"
echo "========================================="
create_project "$DB_PROJECT" "db"
setup_components "$DB_PROJECT" "jcr:"
run_flow_test "$DB_PROJECT" "jcr:"

echo ""
echo "========================================="
echo " 清空测试项目"
echo "========================================="
delete_project "$JCR_PROJECT"
delete_project "$DB_PROJECT"
ok "清空完成"

echo ""
echo "========================================="
echo -e " 结果: ${GREEN}PASS=$PASS${NC}  ${RED}FAIL=$FAIL${NC}"
echo "========================================="
[ "$FAIL" -gt 0 ] && exit 1 || exit 0
