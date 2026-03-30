import React from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Result } from 'antd';
import { CodeOutlined } from '@ant-design/icons';

const ProjectDetail: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();

  return (
    <div style={{ height: 'calc(100vh - 112px)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
      <Result
        icon={<CodeOutlined />}
        title={`项目: ${name}`}
        extra={
          <Button type="primary" onClick={() => navigate(`/console/${name}`)}>
            进入规则编辑器
          </Button>
        }
      />
    </div>
  );
};

export default ProjectDetail;
