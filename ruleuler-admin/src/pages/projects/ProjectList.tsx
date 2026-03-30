import React, { useEffect, useRef, useState } from 'react';
import { Table, Button, Modal, Input, Space, Popconfirm, Upload, message } from 'antd';
import { PlusOutlined, UploadOutlined, DownloadOutlined, DeleteOutlined, SettingOutlined, ExperimentOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { loadProjects, createProject, deleteProject, exportProject, importProject } from '@/api/project';
import type { UploadProps } from 'antd';

interface ProjectItem {
  name: string;
  storageType?: string;
  [key: string]: unknown;
}

export function formatStorageType(value: unknown): string {
  return value === 'db' ? '数据库' : 'JCR';
}

const ProjectList: React.FC = () => {
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
      message.error('加载项目列表失败');
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
    if (!newName.trim()) { message.warning('请输入项目名'); return; }
    setCreating(true);
    try {
      await createProject(newName.trim(), 'db');
      message.success('创建成功');
      setModalOpen(false);
      setNewName('');
      fetchProjects();
    } catch {
      message.error('创建失败');
    } finally {
      setCreating(false);
    }
  };

  const handleDelete = async (name: string) => {
    try {
      await deleteProject(name);
      message.success('删除成功');
      fetchProjects();
    } catch {
      message.error('删除失败');
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
      message.error('导出失败');
    }
  };

  const uploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      try {
        await importProject(file);
        message.success('导入成功');
        fetchProjects();
      } catch {
        message.error('导入失败');
      }
      return false;
    },
  };

  const columns = [
    { title: '项目名称', dataIndex: 'name', key: 'name', render: (name: string) => <a onClick={() => navigate(`/console/${name}`)}>{name}</a> },
    { title: '存储方式', dataIndex: 'storageType', key: 'storageType', render: (value: unknown) => formatStorageType(value) },
    {
      title: '操作', key: 'action', width: 360,
      render: (_: unknown, record: ProjectItem) => (
        <Space>
          <Button size="small" icon={<DownloadOutlined />} onClick={() => handleExport(record.name)}>导出</Button>
          <Button size="small" icon={<SettingOutlined />} onClick={() => navigate(`/projects/${record.name}/client-config`)}>客户端配置</Button>
          <Button size="small" icon={<ExperimentOutlined />} onClick={() => navigate(`/projects/${record.name}/autotest`)}>测试记录</Button>
          <Popconfirm title="确认删除该项目？" onConfirm={() => handleDelete(record.name)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>创建项目</Button>
        <Upload {...uploadProps}><Button icon={<UploadOutlined />}>导入项目</Button></Upload>
      </Space>
      <Table rowKey="name" columns={columns} dataSource={projects} loading={loading} />
      <Modal title="创建项目" open={modalOpen} onOk={handleCreate} confirmLoading={creating} onCancel={() => setModalOpen(false)}>
        <Input placeholder="项目名称" value={newName} onChange={(e) => setNewName(e.target.value)} />
      </Modal>
    </>
  );
};

export default ProjectList;
