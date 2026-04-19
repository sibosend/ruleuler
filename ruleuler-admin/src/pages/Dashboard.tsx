import React, { useState, useEffect } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Spin } from 'antd';
import {
  ProjectOutlined, AppstoreOutlined, ClockCircleOutlined,
  WarningOutlined, PlayCircleOutlined,
  ThunderboltOutlined, AlertOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { Line } from '@ant-design/charts';
import request from '@/api/request';
import dayjs from 'dayjs';

interface DashboardData {
  projectCount: number;
  packageCount: number;
  pendingApprovals: number;
  runningReplayTasks: number;
  todayExecCount: number;
  todayAvgMs: number;
  todayErrorCount: number;
  todayAlertCount: number;
  execTrend: Array<{ day: string; exec_count: number; avg_ms: number }>;
  recentApprovals: Array<{
    id: number; project: string; packageId: string;
    status: string; submitter: string; createdAt: number; publishedAt: number | null;
  }>;
  recentReplays: Array<{
    id: number; project: string; packageId: string; status: string;
    totalCount: number; matchCount: number; mismatchCount: number; createdAt: number;
  }>;
}

const statusColors: Record<string, string> = {
  PENDING: 'orange', APPROVED: 'blue', PUBLISHED: 'green', REJECTED: 'red',
  pending: 'default', running: 'processing', completed: 'success', failed: 'error',
};

const Dashboard: React.FC = () => {
  const username = useAuthStore((s) => s.user?.username);
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    request.get('/api/dashboard')
      .then((res) => setData(res.data?.data))
      .catch(() => {})
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (!data) return <Card>加载失败</Card>;

  // 趋势图数据
  const trendData = (data.execTrend || []).flatMap((d) => [
    { day: d.day, value: d.exec_count, type: '执行量' },
    { day: d.day, value: Math.round(d.avg_ms), type: '平均耗时(ms)' },
  ]);

  const trendConfig = {
    data: trendData,
    xField: 'day',
    yField: 'value',
    colorField: 'type',
    height: 240,
    interaction: { tooltip: { marker: false } },
    axis: { y: { title: false }, x: { title: false } },
    style: { lineWidth: 2 },
  };

  return (
    <div>
      {/* 欢迎 */}
      <Card style={{ marginBottom: 16 }}>
        <span style={{ fontSize: 18 }}>👋 {username ?? '用户'}，欢迎回来</span>
      </Card>

      {/* 核心指标 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card hoverable onClick={() => navigate('/projects')} style={{ cursor: 'pointer' }}>
            <Statistic title="项目数" value={data.projectCount} prefix={<ProjectOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="知识包数" value={data.packageCount} prefix={<AppstoreOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="今日执行量" value={data.todayExecCount} prefix={<ThunderboltOutlined />} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="今日平均耗时" value={data.todayAvgMs.toFixed(1)} suffix="ms"
              prefix={<ClockCircleOutlined />} />
          </Card>
        </Col>
      </Row>

      {/* 待办指标 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card hoverable onClick={() => navigate('/releases/pending')} style={{ cursor: 'pointer' }}>
            <Statistic title="待审核申请" value={data.pendingApprovals}
              prefix={<WarningOutlined />}
              valueStyle={data.pendingApprovals > 0 ? { color: '#faad14' } : undefined} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card hoverable onClick={() => navigate('/releases/replay')} style={{ cursor: 'pointer' }}>
            <Statistic title="运行中回放" value={data.runningReplayTasks}
              prefix={<PlayCircleOutlined />}
              valueStyle={data.runningReplayTasks > 0 ? { color: '#1677ff' } : undefined} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card>
            <Statistic title="今日执行失败" value={data.todayErrorCount}
              valueStyle={data.todayErrorCount > 0 ? { color: '#cf1322' } : undefined} />
          </Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card hoverable onClick={() => navigate('/monitoring/realtime')} style={{ cursor: 'pointer' }}>
            <Statistic title="今日监控告警" value={data.todayAlertCount}
              prefix={<AlertOutlined />}
              valueStyle={data.todayAlertCount > 0 ? { color: '#cf1322' } : undefined} />
          </Card>
        </Col>
      </Row>

      {/* 趋势图 */}
      <Card title="最近 7 天执行趋势" size="small" style={{ marginBottom: 16 }}>
        {trendData.length > 0 ? <Line {...trendConfig} /> : <div style={{ textAlign: 'center', padding: 40, color: '#999' }}>暂无数据</div>}
      </Card>

      {/* 最近活动 */}
      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title="最近上线记录" size="small">
            <Table rowKey="id" dataSource={data.recentApprovals} size="small" pagination={false}
              columns={[
                { title: '项目', dataIndex: 'project', width: 100 },
                { title: '知识包', dataIndex: 'packageId', ellipsis: true },
                { title: '状态', dataIndex: 'status', width: 80,
                  render: (s: string) => <Tag color={statusColors[s]}>{s}</Tag> },
                { title: '提交人', dataIndex: 'submitter', width: 80 },
                { title: '时间', dataIndex: 'createdAt', width: 140,
                  render: (t: number) => t ? dayjs(t).format('MM-DD HH:mm') : '-' },
              ]}
              onRow={() => ({ style: { cursor: 'pointer' }, onClick: () => navigate('/releases/all') })}
            />
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="最近回放任务" size="small">
            <Table rowKey="id" dataSource={data.recentReplays} size="small" pagination={false}
              columns={[
                { title: '项目', dataIndex: 'project', width: 100 },
                { title: '状态', dataIndex: 'status', width: 80,
                  render: (s: string) => <Tag color={statusColors[s]}>{s}</Tag> },
                { title: '总数', dataIndex: 'totalCount', width: 60 },
                { title: '一致率', width: 80,
                  render: (_: unknown, r: any) => {
                    const total = r.matchCount + r.mismatchCount;
                    return total > 0 ? `${(r.matchCount / total * 100).toFixed(1)}%` : '-';
                  }},
                { title: '时间', dataIndex: 'createdAt', width: 140,
                  render: (t: number) => t ? dayjs(t).format('MM-DD HH:mm') : '-' },
              ]}
              onRow={() => ({ style: { cursor: 'pointer' }, onClick: () => navigate('/releases/replay') })}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
};

export default Dashboard;
