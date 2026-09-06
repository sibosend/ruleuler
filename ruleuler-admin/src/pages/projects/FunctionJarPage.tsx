import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Table, Button, Space, Spin, Upload, message } from 'antd';
import { ArrowLeftOutlined, UploadOutlined } from '@ant-design/icons';
import {
  listFunctionJars,
  listFunctionStatus,
  uploadFunctionJar,
  type FunctionJarItem,
  type FunctionStatusItem,
} from '@/api/functionJar';
import { useTranslation } from 'react-i18next';

const FunctionJarPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [jars, setJars] = useState<FunctionJarItem[]>([]);
  const [status, setStatus] = useState<FunctionStatusItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const fetched = useRef(false);

  const load = () => {
    if (!name) return;
    setLoading(true);
    Promise.all([listFunctionJars(name), listFunctionStatus(name)])
      .then(([jarData, statusData]) => {
        setJars(Array.isArray(jarData) ? jarData : []);
        setStatus(Array.isArray(statusData) ? statusData : []);
      })
      .catch(() => message.error(t('project.loadFunctionFailed')))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    if (!name || fetched.current) return;
    fetched.current = true;
    load();
  }, [name]);

  const handleUpload = async (file: File) => {
    if (!name) return false;
    setUploading(true);
    try {
      await uploadFunctionJar(name, file);
      message.success(t('project.uploadFunctionSuccess'));
      load();
    } catch {
      message.error(t('project.uploadFunctionFailed'));
    } finally {
      setUploading(false);
    }
    return false;
  };

  const jarColumns = useMemo(() => [
    { title: t('project.functionPackage'), dataIndex: 'function_package', key: 'function_package' },
    { title: t('project.functionVersion'), dataIndex: 'version', key: 'version' },
    { title: t('project.functionChecksum'), dataIndex: 'checksum', key: 'checksum', ellipsis: true },
    { title: t('project.uploadedBy'), dataIndex: 'uploaded_by', key: 'uploaded_by' },
  ], [t]);

  const statusColumns = useMemo(() => [
    { title: t('project.clientHost'), dataIndex: 'client_host', key: 'client_host' },
    { title: 'packageId', dataIndex: 'package_id', key: 'package_id' },
    { title: t('project.functionPackage'), dataIndex: 'function_package', key: 'function_package' },
    { title: t('project.expectedVersion'), dataIndex: 'expected_version', key: 'expected_version' },
    { title: t('project.actualVersion'), dataIndex: 'actual_version', key: 'actual_version' },
    { title: t('project.functionStatus'), dataIndex: 'status', key: 'status' },
  ], [t]);

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/projects')}>{t('project.backToProjectList')}</Button>
        <span style={{ fontSize: 16, fontWeight: 600 }}>{t('project.functionJars')} - {name}</span>
      </Space>
      <Spin spinning={loading}>
        <Space style={{ marginBottom: 12 }}>
          <Upload accept=".jar" showUploadList={false} beforeUpload={handleUpload}>
            <Button type="primary" icon={<UploadOutlined />} loading={uploading}>{t('project.uploadFunctionJar')}</Button>
          </Upload>
          <span style={{ color: '#888' }}>{t('project.uploadFunctionHint')}</span>
        </Space>
        <Table rowKey={(r) => `${r.function_package}:${r.version}`} columns={jarColumns} dataSource={jars} pagination={false} />
        <div style={{ marginTop: 24, marginBottom: 8, fontWeight: 600 }}>{t('project.functionClientStatus')}</div>
        <Table rowKey={(r) => `${r.client_host}:${r.package_id}:${r.function_package}`} columns={statusColumns} dataSource={status} pagination={false} />
      </Spin>
    </div>
  );
};

export default FunctionJarPage;
