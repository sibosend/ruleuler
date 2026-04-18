import React, { useEffect, useRef, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Space, Popconfirm, message, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined } from '@ant-design/icons';
import { getUsers, createUser, updateUser, deleteUser, assignUserRoles } from '@/api/rbac';
import { getRoles } from '@/api/rbac';
import { useTranslation } from 'react-i18next';

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
  const { t } = useTranslation();
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
      message.error(t('system.loadUsersFailed'));
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
        message.success(t('system.updateSuccess'));
      } else {
        await createUser({ username: values.username, password: values.password });
        message.success(t('system.createSuccess'));
      }
      setEditOpen(false);
      fetchUsers();
    } catch {
      message.error(editUser ? t('system.updateFailed') : t('system.createFailed'));
    }
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteUser(id);
      message.success(t('system.deleteSuccess'));
      fetchUsers();
    } catch {
      message.error(t('system.deleteFailed'));
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
      message.success(t('system.roleAssignSuccess'));
      setRoleOpen(false);
      fetchUsers();
    } catch {
      message.error(t('system.roleAssignFailed'));
    }
  };

  const columns = [
    { title: t('system.username'), dataIndex: 'username', key: 'username' },
    { title: t('common.status'), dataIndex: 'status', key: 'status', render: (s: number) => s === 1 ? <Tag color="green">{t('common.enabled')}</Tag> : <Tag color="red">{t('common.disabled')}</Tag> },
    { title: t('system.roles'), dataIndex: 'roles', key: 'roles', render: (roles?: string[]) => roles?.map((r) => <Tag key={r}>{r}</Tag>) },
    {
      title: t('common.operation'), key: 'action', width: 300,
      render: (_: unknown, record: UserItem) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>{t('system.editBtn')}</Button>
          <Button size="small" icon={<TeamOutlined />} onClick={() => openAssignRoles(record)}>{t('system.assignRoles')}</Button>
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
        <Input.Search placeholder={t('system.searchUsername')} allowClear onSearch={setKeyword} style={{ width: 200 }} />
        <Select allowClear placeholder={t('system.filterByRole')} style={{ width: 160 }} onChange={(v) => setFilterRoleId(v)} options={roles.map((r) => ({ value: r.id, label: r.name }))} />
        <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>{t('system.createUser')}</Button>
      </Space>
      <Table rowKey="id" columns={columns} dataSource={users} loading={loading} />

      <Modal title={editUser ? t('system.editUser') : t('system.createUser')} open={editOpen} onOk={handleSave} onCancel={() => setEditOpen(false)} destroyOnClose>
        <Form form={form} layout="vertical">
          <Form.Item name="username" label={t('system.username')} rules={[{ required: true, message: t('system.enterUsername') }]}>
            <Input />
          </Form.Item>
          <Form.Item name="password" label={t('system.password')} rules={editUser ? [] : [{ required: true, message: t('system.enterPassword') }, { min: 6, message: t('system.passwordMinLen') }]}>
            <Input.Password placeholder={editUser ? t('system.passwordPlaceholder') : ''} />
          </Form.Item>
          {editUser && (
            <Form.Item name="status" label={t('common.status')}>
              <Select options={[{ value: 1, label: t('common.enabled') }, { value: 0, label: t('common.disabled') }]} />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal title={t('system.assignRolesTitle', { name: roleTarget?.username })} open={roleOpen} onOk={handleAssignRoles} onCancel={() => setRoleOpen(false)}>
        <Select mode="multiple" style={{ width: '100%' }} value={selectedRoleIds} onChange={setSelectedRoleIds} options={roles.map((r) => ({ value: r.id, label: r.name }))} />
      </Modal>
    </>
  );
};

export default UserList;
