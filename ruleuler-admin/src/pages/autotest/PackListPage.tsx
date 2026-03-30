import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Table, Tag, Button, Card, Modal, Statistic, message, Space, Select, Row, Col, Upload,
} from 'antd';
import type { RcFile } from 'antd/es/upload';
import {
  PlayCircleOutlined, HistoryOutlined, ThunderboltOutlined, ArrowLeftOutlined, UploadOutlined,
} from '@ant-design/icons';
import { listPacks, listPackages, generatePack, executeRun, loadTestRuns, importPack } from '../../api/autotest';

interface Pack {
  id: number;
  packName: string;
  sourceType: string;
  totalCases: number;
  createdAt: number;
}

interface KnowledgePackage {
  id: string;
  name: string;
}

interface RunOption {
  id: number;
  startedAt: number;
  status: string;
}

const fmt = (v: number) => {
  const d = new Date(v);
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

const PackListPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const packageId = searchParams.get('packageId') ?? undefined;
  const navigate = useNavigate();

  const [knowledgePackages, setKnowledgePackages] = useState<KnowledgePackage[]>([]);
  const [packs, setPacks] = useState<Pack[]>([]);
  const [loading, setLoading] = useState(false);
  const [generating, setGenerating] = useState(false);
  const [runModalOpen, setRunModalOpen] = useState(false);
  const [selectedPackId, setSelectedPackId] = useState<number | null>(null);
  const [baselineRunId, setBaselineRunId] = useState<number | undefined>(undefined);
  const [runOptions, setRunOptions] = useState<RunOption[]>([]);
  const [runLoading, setRunLoading] = useState(false);
  const [importModalOpen, setImportModalOpen] = useState(false);
  const [importing, setImporting] = useState(false);

  // 加载知识包列表
  const fetchKnowledgePackages = useCallback(async () => {
    if (!name) return;
    try {
      const res = await listPackages(name);
      const list = res.data?.data ?? [];
      setKnowledgePackages(Array.isArray(list) ? list : []);
    } catch {
      // 静默失败，下拉为空即可
    }
  }, [name]);

  const fetchPacks = useCallback(async () => {
    if (!name) return;
    setLoading(true);
    try {
      const res = await listPacks(name, packageId);
      const list = res.data?.data ?? [];
      setPacks(Array.isArray(list) ? list : []);
    } catch {
      message.error('加载用例包列表失败');
    } finally {
      setLoading(false);
    }
  }, [name, packageId]);

  const fetched = useRef(false);
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchKnowledgePackages();
    fetchPacks();
  }, [fetchKnowledgePackages, fetchPacks]);

  // packageId 变化时重新加载
  const prevPackageId = useRef(packageId);
  useEffect(() => {
    if (prevPackageId.current === packageId) return;
    prevPackageId.current = packageId;
    fetchPacks();
  }, [packageId, fetchPacks]);

  const handlePackageIdChange = (value: string | undefined) => {
    if (value) {
      setSearchParams({ packageId: value });
    } else {
      setSearchParams({});
    }
  };

  const handleGenerate = async () => {
    if (!name || !packageId) { message.warning('请先选择知识包'); return; }
    setGenerating(true);
    try {
      await generatePack(name, packageId);
      message.success('用例包生成成功');
      fetchPacks();
    } catch {
      message.error('用例包生成失败');
    } finally {
      setGenerating(false);
    }
  };

  const openRunModal = async (packId: number) => {
    setSelectedPackId(packId);
    setBaselineRunId(undefined);
    setRunModalOpen(true);
    if (!name) return;
    try {
      const res = await loadTestRuns(name);
      const allRuns = res.data?.data ?? [];
      const runs: RunOption[] = (Array.isArray(allRuns) ? allRuns : [])
        .filter((r: RunOption) => r.status === 'completed');
      setRunOptions(runs);
    } catch {
      setRunOptions([]);
    }
  };

  const handleRun = async () => {
    if (selectedPackId == null) return;
    setRunLoading(true);
    try {
      await executeRun(selectedPackId, baselineRunId);
      message.success('测试运行已启动');
      setRunModalOpen(false);
      navigate(`/projects/${name}/autotest/pack/${selectedPackId}`);
    } catch {
      message.error('启动运行失败');
    } finally {
      setRunLoading(false);
    }
  };

  const handleImport = async (file: RcFile) => {
    if (!name || !packageId) { message.warning('请先选择知识包'); return false; }
    setImporting(true);
    try {
      await importPack(name, packageId, file);
      message.success('用例包导入成功');
      setImportModalOpen(false);
      fetchPacks();
    } catch {
      // 错误已由 request 拦截器展示
    } finally {
      setImporting(false);
    }
    return false;
  };

  const totalCases = packs.reduce((s, p) => s + p.totalCases, 0);
  const packCount = packs.length;

  const columns = [
    { title: '用例包名称', dataIndex: 'packName', key: 'packName' },
    {
      title: '来源', dataIndex: 'sourceType', key: 'sourceType', width: 100,
      render: (v: string) => (
        <Tag color={v === 'auto' ? 'green' : 'blue'}>{v === 'auto' ? '自动' : '手动'}</Tag>
      ),
    },
    { title: '用例数', dataIndex: 'totalCases', key: 'totalCases', width: 100 },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180,
      render: (v: number) => fmt(v),
    },
    {
      title: '操作', key: 'actions', width: 200,
      render: (_: unknown, record: Pack) => (
        <Space>
          <Button type="link" size="small" icon={<PlayCircleOutlined />}
            onClick={() => openRunModal(record.id)}>运行</Button>
          <Button type="link" size="small" icon={<HistoryOutlined />}
            onClick={() => navigate(`/projects/${name}/autotest/pack/${record.id}`)}>历史运行</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 8 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate(-1)}>返回</Button>
        <Select
          style={{ width: 240 }}
          placeholder="全部知识包"
          allowClear
          value={packageId}
          onChange={handlePackageIdChange}
          options={knowledgePackages.map(p => ({ label: `${p.name} (${p.id})`, value: p.id }))}
        />
        <Button type="primary" icon={<ThunderboltOutlined />}
          loading={generating} onClick={handleGenerate}
          disabled={!packageId}>生成用例包</Button>
        <Button icon={<UploadOutlined />} onClick={() => setImportModalOpen(true)}
          disabled={!packageId}>导入用例</Button>
      </Space>

      <Row gutter={12} style={{ marginBottom: 8 }}>
        <Col span={4}><Card size="small"><Statistic title="用例包数" value={packCount} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="总用例数" value={totalCases} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="自动生成" value={packs.filter(p => p.sourceType === 'auto').length} /></Card></Col>
        <Col span={4}><Card size="small"><Statistic title="手动导入" value={packs.filter(p => p.sourceType === 'manual').length} /></Card></Col>
      </Row>

      <Table columns={columns} dataSource={packs} rowKey="id" loading={loading}
        size="small" pagination={{ pageSize: 20, showTotal: (t) => `共 ${t} 条` }} />

      <Modal title="执行测试运行" open={runModalOpen} onOk={handleRun}
        onCancel={() => setRunModalOpen(false)} confirmLoading={runLoading} okText="开始运行">
        <p>选择 baseline 运行（可选，不选则本次作为 baseline）：</p>
        <Select style={{ width: '100%' }} placeholder="不选择 = 本次作为 baseline"
          allowClear value={baselineRunId} onChange={setBaselineRunId}
          options={runOptions.map(r => ({ label: `Run #${r.id} - ${fmt(r.startedAt)}`, value: r.id }))} />
      </Modal>

      <Modal title="手动导入用例包" open={importModalOpen}
        onCancel={() => setImportModalOpen(false)} footer={null}>
        <Upload.Dragger
          accept=".csv,.json"
          maxCount={1}
          showUploadList={false}
          beforeUpload={handleImport}
          disabled={importing}
        >
          <p className="ant-upload-text">{importing ? '导入中...' : '点击或拖拽 CSV/JSON 文件到此处'}</p>
          <p className="ant-upload-hint">支持 .csv 和 .json 格式</p>
        </Upload.Dragger>
      </Modal>
    </div>
  );
};

export default PackListPage;
