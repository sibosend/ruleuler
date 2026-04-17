import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { Table, Select, DatePicker, Tag, Space, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs, { type Dayjs } from 'dayjs';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { loadProjects } from '../../api/project';
import { listPackages } from '../../api/autotest';
import { fetchExecutions } from '../../api/monitoring';

const { RangePicker } = DatePicker;

interface Execution {
  execution_id: string;
  exec_time: string;
  project: string;
  package_id: string;
  flow_id: string;
  status: string;
  exec_ms: number;
  var_count: number;
  grayscale_bucket?: string;
}

const defaultRange: [Dayjs, Dayjs] = [dayjs().subtract(1, 'day'), dayjs()];

const ExecutionLogPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [projects, setProjects] = useState<string[]>([]);
  const [packages, setPackages] = useState<{ id: string; name: string }[]>([]);
  const [project, setProject] = useState<string | undefined>(() => searchParams.get('project') || undefined);
  const [packageId, setPackageId] = useState<string | undefined>(() => searchParams.get('packageId') || undefined);
  const [dateRange, setDateRange] = useState<[Dayjs, Dayjs]>(defaultRange);

  const [data, setData] = useState<Execution[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  // project/packageId 变更时同步到URL
  useEffect(() => {
    const params = new URLSearchParams();
    if (project) params.set('project', project);
    if (packageId) params.set('packageId', packageId);
    setSearchParams(params, { replace: true });
  }, [project, packageId, setSearchParams]);

  const fetched = useRef(false);

  // 加载项目列表
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    loadProjects()
      .then((res) => {
        const list: string[] = (res.data?.data ?? []).map((p: { name: string }) => p.name);
        setProjects(list);
        // URL没指定project时默认选第一个
        if (!project && list.length > 0) setProject(list[0]);
      })
      .catch(() => message.error('加载项目列表失败'));
  }, []);

  // 项目变更时加载知识包
  const prevProject = useRef<string | undefined>(undefined);
  useEffect(() => {
    if (prevProject.current === project) return;
    prevProject.current = project;
    setPackages([]);
    if (!project) return;
    listPackages(project)
      .then((res) => {
        const list = res.data?.data ?? [];
        const pkgList = Array.isArray(list) ? list : [];
        setPackages(pkgList);
        // URL没指定packageId 或 项目变更时，默认选第一个
        if (pkgList.length > 0) {
          const urlPkgId = searchParams.get('packageId');
          const found = urlPkgId && pkgList.some(p => p.id === urlPkgId);
          setPackageId(found ? urlPkgId : pkgList[0].id);
        } else {
          setPackageId(undefined);
        }
      })
      .catch(() => { /* silent */ });
  }, [project]);

  // 加载执行记录
  const loadData = useCallback(async () => {
    if (!project || !packageId) return;
    setLoading(true);
    try {
      const result = await fetchExecutions({
        project,
        packageId,
        startDate: dateRange[0].format('YYYY-MM-DD'),
        endDate: dateRange[1].format('YYYY-MM-DD'),
        page,
        pageSize,
      });
      setData(Array.isArray(result?.records) ? result.records : Array.isArray(result) ? result : []);
      setTotal(result?.total ?? (Array.isArray(result) ? result.length : 0));
    } catch {
      message.error('加载执行记录失败');
    } finally {
      setLoading(false);
    }
  }, [project, packageId, dateRange, page, pageSize]);

  const prevKey = useRef('');
  useEffect(() => {
    const key = `${project}|${packageId}|${dateRange[0].valueOf()}|${dateRange[1].valueOf()}|${page}|${pageSize}`;
    if (prevKey.current === key) return;
    prevKey.current = key;
    loadData();
  }, [project, packageId, dateRange, page, pageSize, loadData]);

  // 时间范围限制：最大3个月
  const disabledDate = useCallback((current: Dayjs) => {
    return current.isAfter(dayjs()) || current.isBefore(dayjs().subtract(3, 'month'));
  }, []);

  const columns: ColumnsType<Execution> = useMemo(() => [
    {
      title: '执行ID', dataIndex: 'execution_id', key: 'execution_id', width: 120,
      render: (v: string) => <span title={v}>{v.slice(0, 8)}...</span>,
    },
    { title: '执行时间', dataIndex: 'exec_time', key: 'exec_time', width: 180 },
    { title: '项目', dataIndex: 'project', key: 'project', width: 140 },
    { title: '知识包', dataIndex: 'package_id', key: 'package_id', width: 160 },
    { title: 'Flow', dataIndex: 'flow_id', key: 'flow_id', width: 140 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: (v: string) => (
        <Tag color={v === 'success' ? 'green' : 'red'}>{v === 'success' ? '成功' : '失败'}</Tag>
      ),
    },
    {
      title: '灰度', dataIndex: 'grayscale_bucket', key: 'grayscale_bucket', width: 80,
      render: (v: string) => v === 'GRAY'
        ? <Tag color="purple">GRAY</Tag>
        : <Tag color="default">BASE</Tag>,
    },
    {
      title: '耗时(ms)', dataIndex: 'exec_ms', key: 'exec_ms', width: 100,
      sorter: (a, b) => a.exec_ms - b.exec_ms,
    },
    {
      title: '变量数', dataIndex: 'var_count', key: 'var_count', width: 90,
      sorter: (a, b) => a.var_count - b.var_count,
    },
  ], []);

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Select
          style={{ width: 180 }}
          placeholder="选择项目"
          allowClear
          value={project}
          onChange={(v) => { setProject(v); setPage(1); }}
          options={projects.map((p) => ({ label: p, value: p }))}
        />
        <Select
          style={{ width: 220 }}
          placeholder="选择知识包"
          allowClear
          value={packageId}
          onChange={(v) => { setPackageId(v); setPage(1); }}
          disabled={!project}
          options={packages.map((p) => ({ label: `${p.name} (${p.id})`, value: p.id }))}
        />
        <RangePicker
          value={dateRange}
          onChange={(v) => {
            if (v && v[0] && v[1]) { setDateRange([v[0], v[1]]); setPage(1); }
          }}
          disabledDate={disabledDate}
        />
      </Space>

      <Table
        columns={columns}
        dataSource={data}
        rowKey="execution_id"
        loading={loading}
        size="small"
        pagination={{
          current: page,
          pageSize,
          total,
          showTotal: (t) => `共 ${t} 条记录`,
          showSizeChanger: true,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
        onRow={(r) => ({
          onClick: () => navigate(`/monitoring/executions/${r.execution_id}`),
          style: { cursor: 'pointer' },
        })}
      />
    </div>
  );
};

export default ExecutionLogPage;
