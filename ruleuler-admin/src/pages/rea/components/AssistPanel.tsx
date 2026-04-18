import React from 'react';
import { Tabs } from 'antd';
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();

  return (
    <div style={{ display: expanded ? 'block' : 'none' }}>
      <Tabs
        size="small"
        items={[
          {
            key: 'data',
            label: t('rea.dataExplorer'),
            children: <DataExplorer libraries={libraries} onInsert={onInsert} />,
          },
          {
            key: 'examples',
            label: t('rea.exampleMode'),
            children: <Examples type={type} onInsert={onInsert} />,
          },
        ]}
      />
    </div>
  );
};

export default AssistPanel;
