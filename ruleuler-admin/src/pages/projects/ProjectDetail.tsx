import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Result, Space, Divider } from 'antd';
import { CodeOutlined, AuditOutlined, ExperimentOutlined, ThunderboltOutlined, BugOutlined } from '@ant-design/icons';
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
          <Space direction="vertical" size="middle">
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
            <Divider style={{ margin: '8px 0' }} />
            <Space>
              <Button icon={<BugOutlined />} onClick={() => navigate(`/projects/${name}/autotest`)}>
                {t('project.autoTest', '自动测试')}
              </Button>
              <Button icon={<ThunderboltOutlined />} onClick={() => navigate(`/projects/${name}/replay`)}>
                {t('project.trafficReplay', '流量回放')}
              </Button>
              <Button icon={<ExperimentOutlined />} disabled>
                {t('project.simulationTest', '仿真测试')}
              </Button>
            </Space>
          </Space>
        }
      />
    </div>
  );
};

export default ProjectDetail;
