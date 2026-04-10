import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Result, Space } from 'antd';
import { CodeOutlined, AuditOutlined } from '@ant-design/icons';
import { usePermission } from '@/hooks/usePermission';

const ProjectDetail: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const canApproval = usePermission('menu:approvals');

  return (
    <div style={{ height: 'calc(100vh - 112px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Result
        icon={<CodeOutlined />}
        title={`项目: ${name}`}
        extra={
          <Space>
            <Button type="primary" onClick={() => navigate(`/console/${name}`)}>
              进入规则编辑器
            </Button>
            {canApproval && (
              <Button icon={<AuditOutlined />} onClick={() => navigate(`/approvals`)}>
                发布审批
              </Button>
            )}
          </Space>
        }
      />
    </div>
  );
};

export default ProjectDetail;
