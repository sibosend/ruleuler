import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Table, Button, Input, Space, Spin, Popconfirm, message } from 'antd';
import { ArrowLeftOutlined, PlusOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import { loadClientConfig, saveClientConfig, serializeConfigs, type ClientConfigItem } from '@/api/clientConfig';
import { useTranslation } from 'react-i18next';

const ClientConfigPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
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
      .catch(() => message.error(t('project.loadConfigFailed')))
      .finally(() => setLoading(false));
  }, [name]);

  const handleSave = async () => {
    if (!name) return;
    setSaving(true);
    try {
      await saveClientConfig(name, serializeConfigs(configs));
      message.success(t('project.saveSuccess'));
    } catch {
      message.error(t('project.saveFailed'));
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

  const columns = useMemo(() => [
    {
      title: t('project.configName'),
      dataIndex: 'name',
      key: 'name',
      render: (val: string, _rec: ClientConfigItem, idx: number) => (
        <Input value={val} onChange={(e) => handleChange(idx, 'name', e.target.value)} />
      ),
    },
    {
      title: t('project.clientAddress'),
      dataIndex: 'client',
      key: 'client',
      render: (val: string, _rec: ClientConfigItem, idx: number) => (
        <Input value={val} onChange={(e) => handleChange(idx, 'client', e.target.value)} />
      ),
    },
    {
      title: t('common.operation'),
      key: 'action',
      width: 80,
      render: (_: unknown, __: ClientConfigItem, idx: number) => (
        <Popconfirm title={t('project.confirmDelete')} onConfirm={() => handleDelete(idx)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ], [i18n.language, t]);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>{t('project.backToProjectList')}</Button>
        <span style={{ fontSize: 16, fontWeight: 600 }}>客户端配置 - {name}</span>
      </Space>
      <Spin spinning={loading}>
        <Space style={{ marginBottom: 12 }}>
          <Button icon={<PlusOutlined />} onClick={handleAdd}>{t('project.add')}</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} disabled={saving} onClick={handleSave}>{t('common.save')}</Button>
        </Space>
        <Table rowKey={(_, index) => String(index)} columns={columns} dataSource={configs} pagination={false} />
      </Spin>
    </div>
  );
};

export default ClientConfigPage;
