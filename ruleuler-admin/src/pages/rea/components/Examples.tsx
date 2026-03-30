import React from 'react';
import { List, Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';

export interface ExamplesProps {
  type: 'condition' | 'action' | 'else';
  onInsert: (text: string) => void;
}

const CONDITION_EXAMPLES = [
  '变量 == 值',
  '变量 > 值 AND 变量 < 值',
  '变量 In (值1, 值2)',
  '变量 Contain 值',
];

const ASSIGNMENT_EXAMPLES = [
  '变量 = 值',
  '变量 = 变量 + 值',
];

const Examples: React.FC<ExamplesProps> = ({ type, onInsert }) => {
  const items = type === 'condition' ? CONDITION_EXAMPLES : ASSIGNMENT_EXAMPLES;

  return (
    <List
      size="small"
      dataSource={items}
      renderItem={(item) => (
        <List.Item
          className="examples-item"
          style={{ padding: '4px 8px', cursor: 'default' }}
        >
          <span style={{ flex: 1, fontFamily: 'monospace', fontSize: 12 }}>{item}</span>
          <Button
            className="examples-insert-btn"
            type="link"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => onInsert(item)}
          >
            插入
          </Button>
        </List.Item>
      )}
    />
  );
};

export default Examples;
