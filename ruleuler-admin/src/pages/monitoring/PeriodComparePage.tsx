import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Table, Select, DatePicker, Button, Card, Tag, Space, message,
} from 'antd';
import { SwapOutlined, WarningOutlined } from '@ant-design/icons';
import dayjs, { type Dayjs } from 'dayjs';
import { useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import { fetchCompare, fetchRealtimeVariables } from '../../api/monitoring';

const { RangePicker } = DatePicker;

/* ── 导出的纯函数，供属性测试使用 ── */

export function computeDiffPct(a: number | null, b: number | null): number | null {
  if (a == null || b == null) return null;
  if (a === 0) return null;
  return ((b - a) / Math.abs(a)) * 100;
}

export function isDriftSignificant(
  periodA: { missing_rate?: number; mean?: number; std?: number },
  periodB: { missing_rate?: number; mean?: number; std?: number },
): boolean {
  const mrA = periodA.missing_rate ?? 0;
  const mrB = periodB.missing_rate ?? 0;
  if (Math.abs(mrA - mrB) > 0.05) return true;

  const meanA = periodA.mean;
  const meanB = periodB.mean;
  const stdA = periodA.std;
  if (meanA != null && meanB != null && stdA != null && stdA > 0) {
    if (Math.abs(meanA - meanB) > stdA) return true;
  }
  return false;
}

/* ── 类型 ── */

interface VarOption {
  var_category: string;
  var_name: string;
  var_type: string;
}

interface PeriodStats {
  sample_count?: number;
  missing_rate?: number;
  mean?: number;
  std?: number;
  p25?: number;
  p50?: number;
  p75?: number;
  outlier_rate?: number;
  top_value?: string;
  top_freq_ratio?: number;
  distinct_count?: number;
}

interface CompareResult {
  varCategory: string;
  varName: string;
  varType: string;
  periodA: PeriodStats;
  periodB: PeriodStats;
}

/* ── 指标定义 ── */

const NUMERIC_METRICS: { key: keyof PeriodStats; label: string }[] = [
  { key: 'mean', label: 'monitoring.mean' },
  { key: 'std', label: 'monitoring.stddev' },
  { key: 'p25', label: 'monitoring.p25' },
  { key: 'p50', label: 'monitoring.p50' },
  { key: 'p75', label: 'monitoring.p75' },
  { key: 'missing_rate', label: 'monitoring.missingRate' },
  { key: 'outlier_rate', label: 'monitoring.outlierRate' },
];

const CATEGORY_METRICS: { key: keyof PeriodStats; label: string }[] = [
  { key: 'top_value', label: 'monitoring.topFreqValue' },
  { key: 'top_freq_ratio', label: 'monitoring.topFreqRatio' },
  { key: 'distinct_count', label: 'monitoring.distinctCount' },
  { key: 'missing_rate', label: 'monitoring.missingRate' },
];

/* ── 格式化 ── */

function fmtVal(v: unknown): string {
  if (v == null) return '-';
  if (typeof v === 'number') {
    return Number.isInteger(v) ? String(v) : v.toFixed(4);
  }
  return String(v);
}

function fmtDiff(a: unknown, b: unknown): string {
  if (typeof a !== 'number' || typeof b !== 'number') return '-';
  const pct = computeDiffPct(a, b);
  if (pct == null) return 'N/A';
  const sign = pct > 0 ? '+' : '';
  return `${sign}${pct.toFixed(2)}%`;
}

/* ── 3个月限制 ── */

const disabledDate = (current: Dayjs) =>
  current.isAfter(dayjs()) || current.isBefore(dayjs().subtract(3, 'month'));

/* ── 组件 ── */

const PeriodComparePage: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [searchParams] = useSearchParams();
  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string | undefined>(() => searchParams.get('project') || undefined);
  const [packageId, setPackageId] = useState<string | undefined>(() => searchParams.get('packageId') || undefined);
  const [ioType, setIoType] = useState('input');

  const [varOptions, setVarOptions] = useState<VarOption[]>([]);
  const [selectedVars, setSelectedVars] = useState<string[]>([]);

  // 默认周期：A = 上周, B = 本周
  const [periodA, setPeriodA] = useState<[Dayjs, Dayjs]>([dayjs().subtract(14, 'day'), dayjs().subtract(7, 'day')]);
  const [periodB, setPeriodB] = useState<[Dayjs, Dayjs]>([dayjs().subtract(7, 'day'), dayjs()]);

  const [results, setResults] = useState<CompareResult[]>([]);
  const [loading, setLoading] = useState(false);

  const fetched = useRef(false);

  /* 加载项目列表 */
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    loadProjects()
      .then((res) => {
        const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
        setProjects(list);
        if (!project && list.length > 0) setProject(list[0]);
      })
      .catch(() => message.error(t('monitoring.loadProjectsFailed')));
  }, []);

  /* 加载知识包 */
  const prevProject = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (prevProject.current === project) return;
    prevProject.current = project;
    setPackages([]);
    setVarOptions([]);
    setSelectedVars([]);
    if (!project) return;
    listPackages(project)
      .then((res) => {
        const list = res.data?.data ?? [];
        const pkgList = Array.isArray(list) ? list : [];
        setPackages(pkgList);
        if (pkgList.length > 0) {
          const urlPkgId = searchParams.get('packageId');
          const found = urlPkgId && pkgList.some((p: { id: string }) => p.id === urlPkgId);
          setPackageId(found ? urlPkgId : pkgList[0].id);
        }
      })
      .catch(() => { /* silent */ });
  }, [project]);

  /* 加载变量列表 + 自动全选 */
  const prevPkgKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${ioType}`;
    if (prevPkgKey.current === key) return;
    prevPkgKey.current = key;
    setVarOptions([]);
    if (!project || !packageId) return;
    fetchRealtimeVariables({ project, packageId, ioType })
      .then((data) => {
        const list: VarOption[] = (Array.isArray(data) ? data : []).map(
          (v: { var_category: string; var_name: string; var_type: string }) => ({
            var_category: v.var_category,
            var_name: v.var_name,
            var_type: v.var_type,
          }),
        );
        setVarOptions(list);
        // 自动选中所有变量
        setSelectedVars(list.map((v) => `${v.var_category}:${v.var_name}`));
      })
      .catch(() => message.error(t('monitoring.loadVariablesFailed')));
  }, [project, packageId, ioType]);

  /* 执行对比 */
  const handleCompare = useCallback(async () => {
    if (!project || !packageId || selectedVars.length === 0 || !periodA || !periodB) {
      message.warning(t('monitoring.completeSelection'));
      return;
    }
    setLoading(true);
    try {
      const variables = selectedVars.map((key) => {
        const v = varOptions.find((o) => `${o.var_category}:${o.var_name}` === key)!;
        return { varCategory: v.var_category, varName: v.var_name, ioType };
      });
      const data = await fetchCompare({
        project,
        packageId,
        variables,
        periodA: { start: periodA[0].format('YYYY-MM-DD'), end: periodA[1].format('YYYY-MM-DD') },
        periodB: { start: periodB[0].format('YYYY-MM-DD'), end: periodB[1].format('YYYY-MM-DD') },
      });
      setResults(Array.isArray(data) ? data : []);
    } catch {
      message.error(t('monitoring.compareFailed'));
    } finally {
      setLoading(false);
    }
  }, [project, packageId, selectedVars, varOptions, ioType, periodA, periodB]);

  /* 变量加载完成后自动触发对比 */
  const autoCompareKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${selectedVars.length}`;
    if (autoCompareKey.current === key) return;
    autoCompareKey.current = key;
    if (!project || !packageId || selectedVars.length === 0) return;
    handleCompare();
  }, [selectedVars, handleCompare]);

  /* 构建对比表格行 */
  interface MetricRow { metric: string; a: unknown; b: unknown; key: string; drift: boolean }

  const buildMetricRows = (r: CompareResult): MetricRow[] => {
    const isNumeric = r.periodA.mean != null || r.periodB.mean != null;
    const metrics = isNumeric ? NUMERIC_METRICS : CATEGORY_METRICS;
    const drift = isNumeric && isDriftSignificant(r.periodA, r.periodB);

    return metrics.map((m) => ({
      metric: t(m.label),
      a: r.periodA[m.key],
      b: r.periodB[m.key],
      key: m.key,
      drift,
    }));
  };

  const [varFilterOpen, setVarFilterOpen] = useState(false);

  const compareTableColumns = useMemo(() => [
    { title: t('monitoring.metric'), dataIndex: 'metric', width: 120 },
    { title: t('monitoring.periodALabel'), dataIndex: 'a', width: 140, render: (v: unknown) => fmtVal(v) },
    { title: t('monitoring.periodBLabel'), dataIndex: 'b', width: 140, render: (v: unknown) => fmtVal(v) },
    {
      title: t('monitoring.diffPercent'),
      dataIndex: 'key',
      width: 120,
      render: (_: string, row: MetricRow) => {
        const txt = fmtDiff(row.a, row.b);
        const highlight =
          row.drift && (row.key === 'missing_rate' || row.key === 'mean');
        return <span style={highlight ? { color: '#ff4d4f', fontWeight: 600 } : undefined}>{txt}</span>;
      },
    },
  ], [i18n.language]);

  return (
    <div>
      {/* 筛选器 */}
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 180 }}
          placeholder={t('common.selectProject')}
          allowClear
          value={project}
          onChange={setProject}
          options={projects.map((p) => ({ label: p, value: p }))}
        />
        <Select
          style={{ width: 220 }}
          placeholder={t('common.selectPackage')}
          allowClear
          value={packageId}
          onChange={setPackageId}
          disabled={!project}
          options={packages.map((p) => ({ label: `${p.name} (${p.id})`, value: p.id }))}
        />
        <Select
          style={{ width: 100 }}
          value={ioType}
          onChange={setIoType}
          options={[
            { label: t('monitoring.input'), value: 'input' },
            { label: t('monitoring.output'), value: 'output' },
          ]}
        />
        <Button
          size="small"
          type="link"
          onClick={() => setVarFilterOpen(!varFilterOpen)}
        >
          {varFilterOpen ? t('monitoring.collapseVarFilter') : `${t('monitoring.varFilter')}（${t('monitoring.selected', { count: selectedVars.length })}/${varOptions.length}）`}
        </Button>
      </Space>

      {varFilterOpen && (
        <div style={{ marginBottom: 12 }}>
          <Select
            mode="multiple"
            style={{ minWidth: 500 }}
            placeholder={t('monitoring.filterVarPlaceholder')}
            value={selectedVars}
            onChange={setSelectedVars}
            maxTagCount={5}
            options={varOptions.map((v) => ({
              label: `${v.var_category}.${v.var_name}`,
              value: `${v.var_category}:${v.var_name}`,
            }))}
          />
        </div>
      )}

      <Space wrap style={{ marginBottom: 12 }}>
        <span>{t('monitoring.periodA')}</span>
        <RangePicker
          value={periodA}
          onChange={(v) => { if (v && v[0] && v[1]) setPeriodA([v[0], v[1]]); }}
          disabledDate={disabledDate}
        />
        <span>{t('monitoring.periodB')}</span>
        <RangePicker
          value={periodB}
          onChange={(v) => { if (v && v[0] && v[1]) setPeriodB([v[0], v[1]]); }}
          disabledDate={disabledDate}
        />
        <Button type="primary" icon={<SwapOutlined />} onClick={handleCompare} loading={loading}>
          {t('monitoring.compare')}
        </Button>
      </Space>

      {/* 对比结果 */}
      {results.map((r) => {
        const rows = buildMetricRows(r);
        const hasDrift = rows[0]?.drift;
        return (
          <Card
            key={`${r.varCategory}:${r.varName}`}
            size="small"
            title={
              <Space>
                <span>{r.varCategory}.{r.varName}</span>
                <Tag>{r.varType}</Tag>
                {hasDrift && <Tag icon={<WarningOutlined />} color="error">{t('monitoring.significantDrift')}</Tag>}
              </Space>
            }
            style={{ marginBottom: 12 }}
          >
            <Table<MetricRow>
              dataSource={rows}
              rowKey="key"
              size="small"
              pagination={false}
              columns={compareTableColumns}
              rowClassName={(row) =>
                row.drift && (row.key === 'missing_rate' || row.key === 'mean') ? 'drift-row' : ''
              }
            />
          </Card>
        );
      })}

      {results.length === 0 && !loading && (
        <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>
          {t('monitoring.selectVarPeriod')}
        </div>
      )}

      <style>{`.drift-row td { background: #fff1f0 !important; }`}</style>
    </div>
  );
};

export default PeriodComparePage;
