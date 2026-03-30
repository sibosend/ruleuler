import React, { useEffect, useRef, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Space, Popconfirm, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined } from '@ant-design/icons';
import { getUsers, createUser, updateUser, deleteUser, assignUserRoles } from '@/api/rbac';
import { getRoles } from '@/api/rbac';

interface UserItem {
  id: number;
  username: string;
  status: number;
  builtIn: number;
  roles?: string[];
}

interface RoleItem {
  id: number;
  name: string;
}

const UserList: React.FC = () => {
  const [users, setUsers] = useState<UserItem[]>([]);
  const [roles, setRoles] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [filterRoleId, setFilterRoleId] = useState<number | undefined>();
  // create/edit modal
  const [editOpen, setEditOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserItem | null>(null);
  const [form] = Form.useForm();
  // assign roles modal
  const [roleOpen, setRoleOpen] = useState(false);
  const [roleTarget, setRoleTarget] = useState<UserItem | null>(null);
  const [selectedRoleIds, setSelectedRoleIds] = useState<number[]>([]);

  const fetchUsers = async () => {
    setLoading(true);
    try {
      const { data } = await getUsers({ keyword: keyword || undefined, roleId: filterRoleId });
      const list = Array.isArray(data) ? data : (data?.data ?? []);
      setUsers(list);
    } catch {
      message.error('加载用户列表失败');
    } finally {
      setLoading(false);
    }
  };

  const fetchRoles = async () => {
    try {
      const { data } = await getRoles();
      setRoles(Array.isArray(data) ? data : (data?.data ?? []));
    } catch { /* ignore */ }
  };

  const fetched = useRef(false);

  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    fetchRoles();
    fetchUsers();
  }, []);
  useEffect(() => {
    if (!fetched.current) return;
    fetchUsers();
  }, [keyword, filterRoleId]);

  const openCreate = () => { setEditUser(null); form.resetFields(); setEditOpen(true); };
  const openEdit = (u: UserItem) => { setEditUser(u); form.setFieldsValue({ username: u.username, status: u.status }); setEditOpen(true); };

  const handleSave = async () => {
    const values = await form.validateFields();
    try {
      if (editUser) {
        const payload: Record<string, unknown> = { username: values.username, status: values.status };
        if (values.password) payload.password = values.password;
        await updateUser(editUser.id, payload);
        message.success('更新成功');
      } else {
        await createUser({ username: values.username, password: values.password });
        message.success('创建成功');
      }
      setEditOpen(false);
      fetchUsers();
    } catch {
      message.error(editUser ? '更新失败' : '创建失败');
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteUser(id);
      message.success('删除成功');
      fetchUsers();
    } catch {
      message.error('删除失败');
    }
  };

  const openAssignRoles = (u: UserItem) => {
    setRoleTarget(u);
    setSelectedRoleIds(
      (u.roles ?? []).map((name) => roles.find((r) => r.name === name)?.id).filter((id): id is number => id != null),
    );
    setRoleOpen(true);
  };

  const handleAssignRoles = async () => {
    if (!roleTarget) return;
    try {
      await assignUserRoles(roleTarget.id, selectedRoleIds);
      message.success('角色分配成功');
      setRoleOpen(false);
      fetchUsers();
    } catch {
      message.error('角色分配失败');
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (s: number) => s === 1 ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag> },
    { title: '角色', dataIndex: 'roles', key: 'roles', render: (roles?: string[]) => roles?.map((r) => <Tag key={r}>{r}</Tag>) },
    {
      title: '操作', key: 'action', width: 300,
      render: (_: unknown, record: UserItem) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>编辑</Button>
          <Button size="small" icon={<TeamOutlined />} onClick={() => openAssignRoles(record)}>分配角色</Button>
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
        <Input.Search placeholder="搜索用户名" allowClear onSearch={setKeyword} style={{ width: 200 }} />
        <Select allowClear placeholder="按角色筛选" style={{ width: 160 }} onChange={(v) => setFilterRoleId(v)} options={roles.map((r) => ({ value: r.id, label: r.name }))} />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>创建用户</Button>
      </Space>
      <Table rowKey="id" columns={columns} dataSource={users} loading={loading} />

      <Modal title={editUser ? '编辑用户' : '创建用户'} open={editOpen} onOk={handleSave} onCancel={() => setEditOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label="密码" rules={editUser ? [] : [{ required: true, message: '请输入密码' }, { min: 6, message: '密码至少6位' }]}>
            <Input.Password placeholder={editUser ? '留空则不修改' : ''} />
          </Form.Item>
          {editUser && (
            <Form.Item name="status" label="状态">
              <Select options={[{ value: 1, label: '启用' }, { value: 0, label: '禁用' }]} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal title={`分配角色 - ${roleTarget?.username}`} open={roleOpen} onOk={handleAssignRoles} onCancel={() => setRoleOpen(false)}>
        <Select mode="multiple" style={{ width: '100%' }} value={selectedRoleIds} onChange={setSelectedRoleIds} options={roles.map((r) => ({ value: r.id, label: r.name }))} />
      </Modal>
    </>
  );
};

export default UserList;
