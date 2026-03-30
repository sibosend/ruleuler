import React, { useEffect, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Table, Button, Input, Space, Spin, Popconfirm, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import { loadClientConfig, saveClientConfig, serializeConfigs, type ClientConfigItem } from '@/api/clientConfig';

const ClientConfigPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const [configs, setConfigs] = useState<ClientConfigItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const fetched = useRef(false);
  useEffect(() => {
    if (!name || fetched.current) return;
    fetched.current = true;
    setLoading(true);
    loadClientConfig(name)
      .then(({ data }) => {
        const list = Array.isArray(data) ? data : (data as unknown as { data: ClientConfigItem[] })?.data ?? [];
        setConfigs(list);
      })
      .catch(() => message.error('加载客户端配置失败'))
      .finally(() => setLoading(false));
  }, [name]);

  const handleSave = async () => {
    if (!name) return;
    setSaving(true);
    try {
      await saveClientConfig(name, serializeConfigs(configs));
      message.success('保存成功');
    } catch {
      message.error('保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleAdd = () => {
    setConfigs([...configs, { name: '', client: '' }]);
  };

  const handleDelete = (index: number) => {
    setConfigs(configs.filter((_, i) => i !== index));
  };

  const handleChange = (index: number, field: keyof ClientConfigItem, value: string) => {
    setConfigs(configs.map((item, i) => i === index ? { ...item, [field]: value } : item));
  };

  const columns = [
    {
      title: '配置名称',
      dataIndex: 'name',
      key: 'name',
      render: (val: string, _rec: ClientConfigItem, idx: number) => (
        <Input value={val} onChange={(e) => handleChange(idx, 'name', e.target.value)} />
      ),
    },
    {
      title: '客户端地址',
      dataIndex: 'client',
      key: 'client',
      render: (val: string, _rec: ClientConfigItem, idx: number) => (
        <Input value={val} onChange={(e) => handleChange(idx, 'client', e.target.value)} />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 80,
      render: (_: unknown, __: ClientConfigItem, idx: number) => (
        <Popconfirm title="确认删除？" onConfirm={() => handleDelete(idx)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>返回项目列表</Button>
        <span style={{ fontSize: 16, fontWeight: 600 }}>客户端配置 - {name}</span>
      </Space>
      <Spin spinning={loading}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<PlusOutlined />} onClick={handleAdd}>新增</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={saving} onClick={handleSave}>保存</Button>
        </Space>
        <Table rowKey={(_, index) => String(index)} columns={columns} dataSource={configs} pagination={false} />
      </Spin>
    </div>
  );
};

export default ClientConfigPage;
