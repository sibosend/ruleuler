import React from 'react';
import { Tabs } from 'antd';
import type { LibraryData } from '../lib/expressionParser';
import DataExplorer from './DataExplorer';
import Examples from './Examples';

export interface AssistPanelProps {
  expanded: boolean;
  type: 'condition' | 'action' | 'else';
  libraries: LibraryData;
  onInsert: (text: string) => void;
}

const AssistPanel: React.FC<AssistPanelProps> = ({
  expanded,
  type,
  libraries,
  onInsert,
}) => {
  return (
    <div style={{ display: expanded ? 'block' : 'none' }}>
      <Tabs
        size="small"
        items={[
          {
            key: 'data',
            label: '数据浏览',
            children: <DataExplorer libraries={libraries} onInsert={onInsert} />,
          },
          {
            key: 'examples',
            label: '示例模式',
            children: <Examples type={type} onInsert={onInsert} />,
          },
        ]}
      />
    </div>
  );
};

export default AssistPanel;
