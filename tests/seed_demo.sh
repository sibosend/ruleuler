#!/bin/bash
# 写入示例项目到指定存储
# 用法: bash tests/seed_demo.sh <project_name> <jcr|db> [base_url]

set -euo pipefail

PROJECT="${1:?用法: $0 <project_name> <jcr|db> [base_url]}"
STORAGE="${2:?用法: $0 <project_name> <jcr|db> [base_url]}"
BASE_URL="${3:-http://127.0.0.1:16009}"
FRAME_URL="$BASE_URL/urule/frame"
COMMON_URL="$BASE_URL/urule/common"

put_file() {
    local proj="$1" path="$2" type="$3" content="$4" label="$5"
    curl -s -X POST "$FRAME_URL/createFile" -d "path=/${proj}/${path}&type=${type}" > /dev/null 2>&1
    curl -s -X POST "$COMMON_URL/saveFile" \
        -H "Content-Type: application/x-www-form-urlencoded; charset=UTF-8" \
        --data-urlencode "file=/${proj}/${path}" \
        --data-urlencode "content=${content}" \
        --data-urlencode "newVersion=false" > /dev/null
    echo "[OK] $label"
}

# 删除旧项目
curl -s -X POST "$FRAME_URL/deleteFile" -d "path=/${PROJECT}&isFolder=true" > /dev/null 2>&1

# 创建项目
r=$(curl -s -X POST "$FRAME_URL/createProject" -d "newProjectName=${PROJECT}&storageType=${STORAGE}")
if echo "$r" | grep -q "fullPath"; then echo "[OK] 创建项目 $PROJECT ($STORAGE)"
else echo "[FAIL] $r"; exit 1; fi

PFX="jcr:"

put_file "$PROJECT" "flight_info.vl.xml" "vl.xml" '<?xml version="1.0" encoding="utf-8"?>
<variable-library>
  <category name="FlightInfo" clazz="com.test.FlightInfo" type="Custom">
    <var name="aircraft_type" label="机型" type="String" act="InOut"/>
    <var name="flight_number" label="航班号" type="String" act="InOut"/>
    <var name="passenger_count" label="旅客数" type="Integer" act="InOut"/>
    <var name="is_international" label="是否国际" type="Boolean" act="InOut"/>
    <var name="arrival_time" label="到达时间" type="Integer" act="InOut"/>
  </category>
</variable-library>' "变量库-航班信息"

put_file "$PROJECT" "gate_result.vl.xml" "vl.xml" '<?xml version="1.0" encoding="utf-8"?>
<variable-library>
  <category name="GateResult" clazz="com.test.GateResult" type="Custom">
    <var name="assigned_gate" label="分配停机位" type="String" act="InOut"/>
    <var name="gate_type" label="机位类型" type="String" act="InOut"/>
    <var name="priority_score" label="优先级评分" type="Integer" act="InOut"/>
    <var name="reason" label="分配原因" type="String" act="InOut"/>
  </category>
</variable-library>' "变量库-停机坪结果"

put_file "$PROJECT" "intl_priority.rs.xml" "rs.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<rule-set>
  <import-variable-library path=\"${PFX}/${PROJECT}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${PROJECT}/gate_result.vl.xml\"/>
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
</rule-set>" "规则集-国际航班优先级"

put_file "$PROJECT" "gate_assign.dt.xml" "dt.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<decision-table>
  <import-variable-library path=\"${PFX}/${PROJECT}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${PROJECT}/gate_result.vl.xml\"/>
  <row num=\"0\" height=\"40\"/><row num=\"1\" height=\"40\"/><row num=\"2\" height=\"40\"/>
  <col num=\"0\" width=\"180\" type=\"Criteria\" var-category=\"FlightInfo\" var=\"aircraft_type\" datatype=\"String\" var-label=\"机型\"/>
  <col num=\"1\" width=\"180\" type=\"Assignment\" var-category=\"GateResult\" var=\"assigned_gate\" datatype=\"String\" var-label=\"停机位\"/>
  <cell row=\"0\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"B737\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"0\" col=\"1\" rowspan=\"1\"><value content=\"A区-101\" type=\"Input\"/></cell>
  <cell row=\"1\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"A320\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"1\" col=\"1\" rowspan=\"1\"><value content=\"A区-102\" type=\"Input\"/></cell>
  <cell row=\"2\" col=\"0\" rowspan=\"1\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"B777\" type=\"Input\"/></condition></joint></cell>
  <cell row=\"2\" col=\"1\" rowspan=\"1\"><value content=\"B区-201\" type=\"Input\"/></cell>
</decision-table>" "决策表-机型停机坪分配"

put_file "$PROJECT" "pax_routing.dtree.xml" "dtree.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<decision-tree>
  <import-variable-library path=\"${PFX}/${PROJECT}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${PROJECT}/gate_result.vl.xml\"/>
  <variable-tree-node>
    <left var-category=\"FlightInfo\" var=\"passenger_count\" var-label=\"旅客数\" datatype=\"Integer\"/>
    <condition-tree-node op=\"GreaterThen\"><value content=\"200\" type=\"Input\"/>
      <action-tree-node><var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"近机位\" type=\"Input\"/></var-assign></action-tree-node>
    </condition-tree-node>
    <condition-tree-node op=\"LessThenEquals\"><value content=\"200\" type=\"Input\"/>
      <action-tree-node><var-assign var-category=\"GateResult\" var=\"gate_type\" var-label=\"机位类型\" datatype=\"String\"><value content=\"远机位\" type=\"Input\"/></var-assign></action-tree-node>
    </condition-tree-node>
  </variable-tree-node>
</decision-tree>" "决策树-客流量分配"

put_file "$PROJECT" "priority_score.sc" "sc" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<scorecard scoring-type=\"sum\" assign-target-type=\"variable\"
  var-category=\"GateResult\" var=\"priority_score\" datatype=\"Integer\"
  attr-col-category=\"FlightInfo\" attr-col-name=\"评估属性\" attr-col-width=\"180\"
  condition-col-name=\"条件\" condition-col-width=\"180\"
  score-col-name=\"得分\" score-col-width=\"100\">
  <import-variable-library path=\"${PFX}/${PROJECT}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${PROJECT}/gate_result.vl.xml\"/>
  <attribute-row row-number=\"0\"><condition-row row-number=\"0\"/></attribute-row>
  <attribute-row row-number=\"1\"><condition-row row-number=\"1\"/></attribute-row>
  <card-cell row=\"0\" col=\"1\" type=\"attribute\" var=\"passenger_count\" var-label=\"旅客数\" datatype=\"Integer\"/>
  <card-cell row=\"0\" col=\"2\" type=\"condition\"><joint type=\"and\"><condition op=\"GreaterThen\"><value content=\"150\" type=\"Input\"/></condition></joint></card-cell>
  <card-cell row=\"0\" col=\"3\" type=\"score\"><value content=\"30\" type=\"Input\"/></card-cell>
  <card-cell row=\"1\" col=\"1\" type=\"attribute\" var=\"is_international\" var-label=\"是否国际\" datatype=\"Boolean\"/>
  <card-cell row=\"1\" col=\"2\" type=\"condition\"><joint type=\"and\"><condition op=\"Equals\"><value content=\"true\" type=\"Input\"/></condition></joint></card-cell>
  <card-cell row=\"1\" col=\"3\" type=\"score\"><value content=\"50\" type=\"Input\"/></card-cell>
</scorecard>" "评分卡-优先级评分"

put_file "$PROJECT" "gate_allocation.rl.xml" "rl.xml" "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<rule-flow id=\"gate_allocation_flow\">
  <import-variable-library path=\"${PFX}/${PROJECT}/flight_info.vl.xml\"/>
  <import-variable-library path=\"${PFX}/${PROJECT}/gate_result.vl.xml\"/>
  <start name=\"开始\" x=\"40\" y=\"200\" width=\"50\" height=\"50\">
    <connection g=\"\" to=\"国际航班规则\"/>
  </start>
  <rule name=\"国际航班规则\" file=\"${PFX}/${PROJECT}/intl_priority.rs.xml\" version=\"LATEST\" x=\"160\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"机型分配\"/>
  </rule>
  <rule name=\"机型分配\" file=\"${PFX}/${PROJECT}/gate_assign.dt.xml\" version=\"LATEST\" x=\"340\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"客流量判断\"/>
  </rule>
  <rule name=\"客流量判断\" file=\"${PFX}/${PROJECT}/pax_routing.dtree.xml\" version=\"LATEST\" x=\"520\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"优先级评分\"/>
  </rule>
  <rule name=\"优先级评分\" file=\"${PFX}/${PROJECT}/priority_score.sc\" version=\"LATEST\" x=\"700\" y=\"200\" width=\"120\" height=\"50\">
    <connection g=\"\" to=\"结束\"/>
  </rule>
  <end name=\"结束\" x=\"880\" y=\"200\" width=\"50\" height=\"50\"/>
</rule-flow>" "决策流-停机坪分配主流程"

echo ""
echo "=== 示例项目 ${PROJECT} 已写入 ${STORAGE} 存储 ==="
