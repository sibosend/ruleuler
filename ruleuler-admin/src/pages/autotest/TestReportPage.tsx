import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Row, Col, Statistic, Table, Alert, Tag, Button, Space, message, Anchor,
} from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { Pie, Column } from '@ant-design/charts';
import { getReport, getConflicts, updateBaseline } from '../../api/autotest';

interface Summary {
  totalCases: number;
  sameCount: number;
  changedCount: number;
  consistencyRate: number;
}
interface SegmentItem {
  variableName: string;
  variableType: string;
  segmentLabel: string;
  caseCount: number;
  percentage: number;
  baselineCount: number | null;
  baselinePercentage: number | null;
  changePct: number | null;
}
interface ChangeDetail {
  inputData: string;
  baselineOutput: string;
  actualOutput: string;
  diffStatus: string;
}
interface ConflictItem {
  conflictType: string;
  severity: string;
  ruleFile: string;
  location: string;
  description: string;
}

const groupSegments = (segments: SegmentItem[]) => {
  const map = new Map<string, SegmentItem[]>();
  for (const seg of segments) {
    const key = `${seg.variableType}::${seg.variableName}`;
    if (!map.has(key)) map.set(key, []);
    map.get(key)!.push(seg);
  }
  return map;
};

const TestReportPage: React.FC = () => {
  const { runId } = useParams<{ name: string; runId: string }>();
  const navigate = useNavigate();
  const rid = Number(runId);

  const [summary, setSummary] = useState<Summary>({ totalCases: 0, sameCount: 0, changedCount: 0, consistencyRate: 0 });
  const [segments, setSegments] = useState<SegmentItem[]>([]);
  const [changeDetails, setChangeDetails] = useState<ChangeDetail[]>([]);
  const [conflicts, setConflicts] = useState<ConflictItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [updatingBaseline, setUpdatingBaseline] = useState(false);

  const fetchData = useCallback(async () => {
    if (!runId) return;
    setLoading(true);
    try {
      const [rptRes, cfRes] = await Promise.all([getReport(rid), getConflicts(rid)]);
      const rpt = rptRes.data?.data ?? rptRes.data ?? {};
      setSummary(rpt.summary ?? { totalCases: 0, sameCount: 0, changedCount: 0, consistencyRate: 0 });
      setSegments(rpt.segments ?? []);
      setChangeDetails(rpt.changeDetails ?? []);
      const cl = cfRes.data?.data ?? cfRes.data ?? [];
      setConflicts(Array.isArray(cl) ? cl : []);
    } catch { message.error('加载测试报告失败'); }
    finally { setLoading(false); }
  }, [runId, rid]);

  const fetched = useRef(false);
  useEffect(() => { if (fetched.current) return; fetched.current = true; fetchData(); }, [fetchData]);

  const handleUpdateBaseline = async () => {
    setUpdatingBaseline(true);
    try { await updateBaseline(rid); message.success('已更新为新的 Baseline'); fetchData(); }
    catch { message.error('更新 Baseline 失败'); }
    finally { setUpdatingBaseline(false); }
  };

  const grouped = groupSegments(segments);
  const outputGroups: [string, SegmentItem[]][] = [];
  const inputGroups: [string, SegmentItem[]][] = [];
  for (const [key, items] of grouped) {
    const [type, varName] = key.split('::');
    if (type === 'OUTPUT') outputGroups.push([varName ?? key, items]);
    else inputGroups.push([varName ?? key, items]);
  }
  // 按变量名字母升序排
  outputGroups.sort((a, b) => a[0].localeCompare(b[0]));
  inputGroups.sort((a, b) => a[0].localeCompare(b[0]));

  // Anchor 导航 — 输出和输入分组
  const anchorItems: { key: string; href: string; title: string; children?: { key: string; href: string; title: string }[] }[] = [
    { key: 'summary', href: '#summary', title: '汇总' },
  ];
  if (conflicts.length > 0) anchorItems.push({ key: 'conflicts', href: '#conflicts', title: '冲突检测' });
  if (outputGroups.length > 0) {
    anchorItems.push({
      key: 'output-dist', href: '#output-dist', title: '输出分布',
      children: outputGroups.map(([v]) => ({ key: `out-${v}`, href: `#out-${v}`, title: v })),
    });
  }
  if (inputGroups.length > 0) {
    anchorItems.push({
      key: 'input-cov', href: '#input-cov', title: '输入覆盖',
      children: inputGroups.map(([v]) => ({ key: `in-${v}`, href: `#in-${v}`, title: v })),
    });
  }
  if (changeDetails.length > 0) anchorItems.push({ key: 'changes', href: '#changes', title: `变化明细 (${changeDetails.length})` });

  // 输出分布：有 baseline 用分组柱状图，无 baseline 用饼图
  const renderOutputSection = (varName: string, items: SegmentItem[]) => {
    const sorted = [...items].sort((a, b) => b.caseCount - a.caseCount);
    const hasBaseline = sorted.some(s => s.baselineCount != null);
    const cols: any[] = [
      { title: '值', dataIndex: 'segmentLabel', key: 'sl' },
      { title: '条数', dataIndex: 'caseCount', key: 'cc', width: 80, defaultSortOrder: 'descend' as const, sorter: (a: SegmentItem, b: SegmentItem) => a.caseCount - b.caseCount },
      { title: '占比', dataIndex: 'percentage', key: 'pct', width: 80, render: (v: number) => `${v}%` },
    ];
    if (hasBaseline) {
      cols.push(
        { title: 'Baseline', dataIndex: 'baselineCount', key: 'bc', width: 80, render: (v: number | null) => v ?? '-' },
        { title: 'Baseline%', dataIndex: 'baselinePercentage', key: 'bp', width: 80, render: (v: number | null) => v != null ? `${v}%` : '-' },
        { title: '变化', dataIndex: 'changePct', key: 'cp', width: 90,
          render: (v: number | null) => {
            if (v == null) return '-';
            const warn = Math.abs(v) > 5;
            const color = v > 0 ? '#f5222d' : v < 0 ? '#52c41a' : undefined;
            return <span style={{ color, fontWeight: warn ? 600 : 400 }}>{warn && '⚠️ '}{v > 0 ? '+' : ''}{v}%</span>;
          },
        },
      );
    }

    let chart: React.ReactNode;
    if (hasBaseline) {
      const barData: { label: string; value: number; group: string }[] = [];
      for (const s of sorted) {
        barData.push({ label: s.segmentLabel, value: s.caseCount, group: '本次' });
        barData.push({ label: s.segmentLabel, value: s.baselineCount ?? 0, group: 'Baseline' });
      }
      chart = (
        <Column data={barData} xField="label" yField="value" colorField="group"
          group height={200}
          scale={{ color: { range: ['#1677ff', '#d9d9d9'] } }} />
      );
    } else {
      const pieData = sorted.map(s => ({ type: s.segmentLabel, value: s.caseCount }));
      chart = (
        <Pie data={pieData} angleField="value" colorField="type"
          height={200} innerRadius={0.5}
          label={{ text: 'type', position: 'outside', style: { fontSize: 11 } }}
          legend={false} />
      );
    }

    return (
      <div key={varName} id={`out-${varName}`} style={{ marginBottom: 16 }}>
        <Card title={varName} size="small">
          <Row gutter={16}>
            <Col span={10}>{chart}</Col>
            <Col span={14}>
              <Table columns={cols} dataSource={sorted} rowKey="segmentLabel"
                size="small" pagination={false} />
            </Col>
          </Row>
        </Card>
      </div>
    );
  };

  // 输入覆盖：表格
  const renderInputSection = (varName: string, items: SegmentItem[]) => {
    const sorted = [...items].sort((a, b) => b.caseCount - a.caseCount);
    return (
      <div key={varName} id={`in-${varName}`} style={{ marginBottom: 16 }}>
        <Card title={varName} size="small">
          <Table
            columns={[
              { title: '区间', dataIndex: 'segmentLabel', key: 'sl' },
              { title: '用例数', dataIndex: 'caseCount', key: 'cc', width: 80, defaultSortOrder: 'descend' as const, sorter: (a: SegmentItem, b: SegmentItem) => a.caseCount - b.caseCount },
              { title: '占比', dataIndex: 'percentage', key: 'pct', width: 80, render: (v: number) => `${v}%` },
            ]}
            dataSource={sorted} rowKey="segmentLabel" size="small" pagination={false} />
        </Card>
      </div>
    );
  };

  const sortKeys = (obj: unknown): unknown => {
    if (Array.isArray(obj)) return obj.map(sortKeys);
    if (obj && typeof obj === 'object') {
      return Object.keys(obj as Record<string, unknown>).sort((a, b) => a.localeCompare(b)).reduce((acc, k) => {
        acc[k] = sortKeys((obj as Record<string, unknown>)[k]);
        return acc;
      }, {} as Record<string, unknown>);
    }
    return obj;
  };
  const fmtJson = (s: string) => {
    if (!s) return '-';
    try { return JSON.stringify(sortKeys(JSON.parse(s)), null, 2); } catch { return s; }
  };

  const detailCols = [
    { title: '输入', dataIndex: 'inputData', key: 'in',
      render: (v: string) => <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{fmtJson(v)}</pre> },
    { title: 'Baseline输出', dataIndex: 'baselineOutput', key: 'bo',
      render: (v: string) => <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{fmtJson(v)}</pre> },
    { title: '本次输出', dataIndex: 'actualOutput', key: 'ao',
      render: (v: string, r: ChangeDetail) => (
        <pre style={{ margin: 0, fontSize: 12, whiteSpace: 'pre-wrap', wordBreak: 'break-all',
          background: r.diffStatus === 'CHANGED' ? '#fff1f0' : undefined, padding: '2px 4px', borderRadius: 2 }}>
          {fmtJson(v)}
        </pre>
      ),
    },
    { title: '状态', dataIndex: 'diffStatus', key: 'ds', width: 80, render: () => <Tag color="red">CHANGED</Tag> },
  ];

  return (
    <div style={{ display: 'flex', gap: 16 }}>
      {/* 左侧 Anchor 导航 */}
      <div style={{ width: 160, flexShrink: 0 }}>
        <Anchor
          offsetTop={80}
          items={anchorItems}
        />
      </div>

      {/* 右侧内容 */}
      <div style={{ flex: 1, minWidth: 0 }}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
          <span style={{ fontSize: 15, fontWeight: 500 }}>测试报告 Run #{runId}</span>
        </Space>

        {/* 汇总 */}
        <div id="summary">
          <Row gutter={12} style={{ marginBottom: 12 }}>
            <Col span={6}><Card size="small"><Statistic title="总用例" value={summary.totalCases} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="一致" value={summary.sameCount} valueStyle={{ color: '#52c41a' }} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="不一致" value={summary.changedCount}
              valueStyle={summary.changedCount > 0 ? { color: '#f5222d' } : undefined} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="一致率" value={summary.consistencyRate} suffix="%" precision={1} /></Card></Col>
          </Row>
        </div>

        {summary.changedCount > 0 && (
          <Alert style={{ marginBottom: 12 }} type="warning" showIcon
            message={`本次运行有 ${summary.changedCount} 条变化`}
            action={
              <Button size="small" type="primary" icon={<CheckCircleOutlined />}
                loading={updatingBaseline} onClick={handleUpdateBaseline}>
                确认变化并更新 Baseline
              </Button>
            } />
        )}

        {/* 冲突 */}
        {conflicts.length > 0 && (
          <div id="conflicts" style={{ marginBottom: 12 }}>
            <Card title="冲突检测" size="small">
              {conflicts.map((c, i) => (
                <Alert key={i} style={{ marginBottom: 4 }}
                  type={c.severity === 'ERROR' ? 'error' : 'warning'}
                  message={`[${c.conflictType}] ${c.ruleFile}`}
                  description={c.description} showIcon />
              ))}
            </Card>
          </div>
        )}

        {/* 输出分布 */}
        {outputGroups.length > 0 && (
          <div id="output-dist">
            <h4 style={{ margin: '12px 0 8px' }}>输出分布</h4>
            {outputGroups.map(([v, items]) => renderOutputSection(v, items))}
          </div>
        )}

        {/* 输入覆盖 */}
        {inputGroups.length > 0 && (
          <div id="input-cov">
            <h4 style={{ margin: '12px 0 8px' }}>输入覆盖</h4>
            {inputGroups.map(([v, items]) => renderInputSection(v, items))}
          </div>
        )}

        {/* 变化明细 */}
        {changeDetails.length > 0 && (
          <div id="changes" style={{ marginTop: 12 }}>
            <Card title={`变化明细（${changeDetails.length} 条）`} size="small">
              <Table columns={detailCols} dataSource={changeDetails} rowKey={(_, i) => String(i)}
                size="small" pagination={{ pageSize: 20, showTotal: t => `共 ${t} 条` }} loading={loading} />
            </Card>
          </div>
        )}
      </div>
    </div>
  );
};

export default TestReportPage;
