import React, { useEffect, useRef, useState } from 'react';
import { Table, Button, Modal, Form, Input, Space, Popconfirm, Tree, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyOutlined } from '@ant-design/icons';
import { getRoles, createRole, updateRole, deleteRole, assignRolePermissions, getPermissions } from '@/api/rbac';
import type { DataNode } from 'antd/es/tree';

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
      message.error('加载角色列表失败');
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
        message.success('更新成功');
      } else {
        await createRole(values);
        message.success('创建成功');
      }
      setEditOpen(false);
      fetchRoles();
    } catch {
      message.error(editRole ? '更新失败' : '创建失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteRole(id);
      message.success('删除成功');
      fetchRoles();
    } catch {
      message.error('删除失败');
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
      message.success('权限分配成功');
      setPermOpen(false);
      fetchRoles();
    } catch {
      message.error('权限分配失败');
    }
  };

  const columns = [
    { title: '角色名', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    {
      title: '操作', key: 'action', width: 320,
      render: (_: unknown, record: RoleItem) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" icon={<SafetyOutlined />} onClick={() => openAssignPerms(record)}>分配权限</Button>
          <Popconfirm title="确认删除？" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />} disabled={record.builtIn === 1}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建角色</Button>
      </Space>
      <Table rowKey="id" columns={columns} dataSource={roles} loading={loading} />

      <Modal title={editRole ? '编辑角色' : '创建角色'} open={editOpen} onOk={handleSave} onCancel={() => setEditOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="角色名" rules={[{ required: true, message: '请输入角色名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      <Modal title={`分配权限 - ${permTarget?.name}`} open={permOpen} onOk={handleAssignPerms} onCancel={() => setPermOpen(false)}>
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
