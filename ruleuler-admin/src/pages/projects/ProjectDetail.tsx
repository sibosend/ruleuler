import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Result, Space } from 'antd';
import { CodeOutlined, AuditOutlined } from '@ant-design/icons';
import { usePermission } from '@/hooks/usePermission';
import { useTranslation } from 'react-i18next';

const ProjectDetail: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const canApproval = usePermission('menu:approvals');
  const { t } = useTranslation();

  return (
    <div style={{ height: 'calc(100vh - 112px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Result
        icon={<CodeOutlined />}
        title={`项目: ${name}`}
        extra={
          <Space>
            <Button type="primary" onClick={() => navigate(`/console/${name}`)}>
              {t('project.enterRuleEditor')}
            </Button>
            {canApproval && (
              <Button icon={<AuditOutlined />} onClick={() => navigate(`/releases/my`)}>
                {t('project.releaseManagement')}
              </Button>
            )}
          </Space>
        }
      />
    </div>
  );
};

export default ProjectDetail;
