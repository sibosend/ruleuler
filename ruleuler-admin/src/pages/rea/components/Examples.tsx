import React from 'react';
import { List, Button } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';

export interface ExamplesProps {
  type: 'condition' | 'action' | 'else';
  onInsert: (text: string) => void;
}

const CONDITION_EXAMPLE_KEYS = [
  'rea.exampleCondition1',
  'rea.exampleCondition2',
  'rea.exampleCondition3',
  'rea.exampleCondition4',
];

const ASSIGNMENT_EXAMPLE_KEYS = [
  'rea.exampleAction1',
  'rea.exampleAction2',
];

const Examples: React.FC<ExamplesProps> = ({ type, onInsert }) => {
  const { t } = useTranslation();
  const exampleKeys = type === 'condition' ? CONDITION_EXAMPLE_KEYS : ASSIGNMENT_EXAMPLE_KEYS;
  const items = exampleKeys.map(key => t(key));

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
            {t('rea.insert')}
          </Button>
        </List.Item>
      )}
    />
  );
};

export default Examples;
