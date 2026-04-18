import React, { useEffect, useRef, useState } from 'react';
import { Table, Button, Modal, Form, Input, Space, Popconfirm, Tree, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyOutlined } from '@ant-design/icons';
import { getRoles, createRole, updateRole, deleteRole, assignRolePermissions, getPermissions } from '@/api/rbac';
import type { DataNode } from 'antd/es/tree';
import { useTranslation } from 'react-i18next';

interface RoleItem {
  id: number;
  name: string;
  description?: string;
  builtIn: number;
  permissions?: { id: number; permissionCode: string }[];
}

interface PermItem {
  id: number;
  permissionCode: string;
  name: string;
  parentId: number | null;
  children?: PermItem[];
}

function buildTree(perms: PermItem[]): DataNode[] {
  const map = new Map<number, PermItem & { children: PermItem[] }>();
  const roots: (PermItem & { children: PermItem[] })[] = [];
  for (const p of perms) {
    map.set(p.id, { ...p, children: [] });
  }
  for (const p of perms) {
    const node = map.get(p.id)!;
    if (p.parentId && map.has(p.parentId)) {
      map.get(p.parentId)!.children.push(node);
    } else {
      roots.push(node);
    }
  }
  const toNode = (items: (PermItem & { children: PermItem[] })[]): DataNode[] =>
    items.map((i) => ({ key: i.id, title: i.name, children: i.children.length > 0 ? toNode(i.children as (PermItem & { children: PermItem[] })[]) : undefined }));
  return toNode(roots);
}

const RoleList: React.FC = () => {
  const { t } = useTranslation();
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [editRole, setEditRole] = useState<RoleItem | null>(null);
  const [form] = Form.useForm();
  // permission assignment
  const [permOpen, setPermOpen] = useState(false);
  const [permTarget, setPermTarget] = useState<RoleItem | null>(null);
  const [permTree, setPermTree] = useState<DataNode[]>([]);
  const [checkedKeys, setCheckedKeys] = useState<number[]>([]);

  const fetchRoles = async () => {
    setLoading(true);
    try {
      const { data } = await getRoles();
      setRoles(Array.isArray(data) ? data : (data?.data ?? []));
    } catch {
      message.error(t('system.loadRolesFailed'));
    } finally {
      setLoading(false);
    }
  };

  const fetchPermissions = async () => {
    try {
      const { data } = await getPermissions();
      const list: PermItem[] = Array.isArray(data) ? data : (data?.data ?? []);
      setPermTree(buildTree(list));
    } catch { /* ignore */ }
  };

  const fetched = useRef(false);
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchRoles();
    fetchPermissions();
  }, []);

  const openCreate = () => { setEditRole(null); form.resetFields(); setEditOpen(true); };
  const openEdit = (r: RoleItem) => { setEditRole(r); form.setFieldsValue({ name: r.name, description: r.description }); setEditOpen(true); };

  const handleSave = async () => {
    const values = await form.validateFields();
    try {
      if (editRole) {
        await updateRole(editRole.id, values);
        message.success(t('system.updateSuccess'));
      } else {
        await createRole(values);
        message.success(t('system.createSuccess'));
      }
      setEditOpen(false);
      fetchRoles();
    } catch {
      message.error(editRole ? t('system.updateFailed') : t('system.createFailed'));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteRole(id);
      message.success(t('system.deleteSuccess'));
      fetchRoles();
    } catch {
      message.error(t('system.deleteFailed'));
    }
  };

  const openAssignPerms = (r: RoleItem) => {
    setPermTarget(r);
    setCheckedKeys(r.permissions?.map((p) => p.id) ?? []);
    setPermOpen(true);
  };

  const handleAssignPerms = async () => {
    if (!permTarget) return;
    try {
      await assignRolePermissions(permTarget.id, checkedKeys);
      message.success(t('system.permAssignSuccess'));
      setPermOpen(false);
      fetchRoles();
    } catch {
      message.error(t('system.permAssignFailed'));
    }
  };

  const columns = [
    { title: t('system.roleName'), dataIndex: 'name', key: 'name' },
    { title: t('system.description'), dataIndex: 'description', key: 'description' },
    {
      title: t('common.operation'), key: 'action', width: 320,
      render: (_: unknown, record: RoleItem) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>{t('system.editBtn')}</Button>
          <Button size="small" icon={<SafetyOutlined />} onClick={() => openAssignPerms(record)}>{t('system.assignPerms')}</Button>
          <Popconfirm title={t('common.confirmDelete')} onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} disabled={record.builtIn === 1}>{t('common.delete')}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>{t('system.createRole')}</Button>
      </Space>
      <Table rowKey="id" columns={columns} dataSource={roles} loading={loading} />

      <Modal title={editRole ? t('system.editRole') : t('system.createRole')} open={editOpen} onOk={handleSave} onCancel={() => setEditOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label={t('system.roleName')} rules={[{ required: true, message: t('system.enterRoleName') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label={t('system.description')}>
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={t('system.assignPermsTitle', { name: permTarget?.name })} open={permOpen} onOk={handleAssignPerms} onCancel={() => setPermOpen(false)}>
        <Tree
          checkable
          treeData={permTree}
          checkedKeys={checkedKeys}
          onCheck={(keys) => setCheckedKeys((Array.isArray(keys) ? keys : keys.checked) as number[])}
        />
      </Modal>
    </>
  );
};

export default RoleList;
