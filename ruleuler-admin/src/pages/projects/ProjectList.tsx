import React, { useEffect, useMemo, useRef, useState } from 'react';
import { Table, Button, Modal, Input, Space, Popconfirm, Upload, message } from 'antd';
import { PlusOutlined, UploadOutlined, DownloadOutlined, DeleteOutlined, SettingOutlined, ExperimentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { loadProjects, createProject, deleteProject, exportProject, importProject } from '@/api/project';
import { useTranslation } from 'react-i18next';
import type { UploadProps } from 'antd';

interface ProjectItem {
  name: string;
  storageType?: string;
  [key: string]: unknown;
}

export function formatStorageType(value: unknown, t: (key: string) => string): string {
  return value === 'db' ? t('project.storageDb') : t('project.storageJcr');
}

const ProjectList: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [projects, setProjects] = useState<ProjectItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [newName, setNewName] = useState('');
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();

  const fetched = useRef(false);

  const fetchProjects = async () => {
    setLoading(true);
    try {
      const { data } = await loadProjects();
      // 兼容 { code, data: [...] } 或直接数组
      const list = Array.isArray(data) ? data : (data?.data ?? []);
      setProjects(list.map((item: string | ProjectItem) => typeof item === 'string' ? { name: item } : item));
    } catch {
      message.error(t('project.loadFailed'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchProjects();
  }, []);

  const handleCreate = async () => {
    if (!newName.trim()) { message.warning(t('project.enterProjectName')); return; }
    setCreating(true);
    try {
      await createProject(newName.trim(), 'db');
      message.success(t('project.createSuccess'));
      setModalOpen(false);
      setNewName('');
      fetchProjects();
    } catch {
      message.error(t('project.createFailed'));
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (name: string) => {
    try {
      await deleteProject(name);
      message.success(t('project.deleteSuccess'));
      fetchProjects();
    } catch {
      message.error(t('project.deleteFailed'));
    }
  };

  const handleExport = async (name: string) => {
    try {
      const { data } = await exportProject(name);
      const url = window.URL.createObjectURL(new Blob([data]));
      const a = document.createElement('a');
      a.href = url;
      a.download = `${name}.zip`;
      a.click();
      window.URL.revokeObjectURL(url);
    } catch {
      message.error(t('project.exportFailed'));
    }
  };

  const uploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      try {
        await importProject(file);
        message.success(t('project.importSuccess'));
        fetchProjects();
      } catch {
        message.error(t('project.importFailed'));
      }
      return false;
    },
  };

  const columns = useMemo(() => [
    { title: t('project.projectName'), dataIndex: 'name', key: 'name', render: (name: string) => <a onClick={() => navigate(`/console/${name}`)}>{name}</a> },
    { title: t('project.storageType'), dataIndex: 'storageType', key: 'storageType', render: (value: unknown) => formatStorageType(value, t) },
    {
      title: t('common.operation'), key: 'action', width: 360,
      render: (_: unknown, record: ProjectItem) => (
        <Space>
          <Button size="small" icon={<DownloadOutlined />} onClick={() => handleExport(record.name)}>{t('common.export')}</Button>
          <Button size="small" icon={<SettingOutlined />} onClick={() => navigate(`/projects/${record.name}/client-config`)}>{t('project.clientConfigBtn')}</Button>
          <Button size="small" icon={<ExperimentOutlined />} onClick={() => navigate(`/projects/${record.name}/autotest`)}>{t('project.testRecordsBtn')}</Button>
          <Popconfirm title={t('project.confirmDeleteProject')} onConfirm={() => handleDelete(record.name)}>
            <Button size="small" danger icon={<DeleteOutlined />}>{t('common.delete')}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ], [i18n.language, navigate, t]);

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>{t('project.createProject')}</Button>
        <Upload {...uploadProps}><Button icon={<UploadOutlined />}>{t('project.importProject')}</Button></Upload>
      </Space>
      <Table rowKey="name" columns={columns} dataSource={projects} loading={loading} />
      <Modal title={t('project.createProject')} open={modalOpen} onOk={handleCreate} confirmLoading={creating} onCancel={() => setModalOpen(false)}>
        <Input placeholder={t('project.projectName')} value={newName} onChange={(e) => setNewName(e.target.value)} />
      </Modal>
    </>
  );
};

export default ProjectList;
