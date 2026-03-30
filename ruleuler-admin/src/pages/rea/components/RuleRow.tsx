import React from 'react';
import { Typography, Dropdown, Button, Popconfirm, Space, InputNumber, Switch } from 'antd';
import {
  SettingOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import type { RuleState } from '../types';
import type { LibraryData } from '../lib/expressionParser';
import ExpressionArea from './ExpressionArea';

export interface RuleRowProps {
  rule: RuleState;
  onUpdate: (id: string, updates: Partial<RuleState>) => void;
  onDelete: (id: string) => void;
  libraries: LibraryData;
}

/** 属性 Dropdown 菜单项 */
function buildPropertyMenuItems(
  rule: RuleState,
  onUpdate: RuleRowProps['onUpdate'],
): MenuProps['items'] {
  return [
    {
      key: 'salience',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          优先级
          <InputNumber
            size="small"
            style={{ width: 80 }}
            value={(rule.properties.salience as number) ?? 0}
            onClick={(e) => e.stopPropagation()}
            onChange={(v) =>
              onUpdate(rule.id, {
                properties: { ...rule.properties, salience: v ?? 0 },
              })
            }
          />
        </span>
      ),
    },
    {
      key: 'enabled',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          启用
          <Switch
            size="small"
            checked={rule.properties.enabled !== false}
            onChange={(v) =>
              onUpdate(rule.id, {
                properties: { ...rule.properties, enabled: v },
              })
            }
          />
        </span>
      ),
    },
    {
      key: 'debug',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          调试
          <Switch
            size="small"
            checked={rule.properties.debug === true}
            onChange={(v) =>
              onUpdate(rule.id, {
                properties: { ...rule.properties, debug: v },
              })
            }
          />
        </span>
      ),
    },
    {
      key: 'loop',
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          循环
          <Switch
            size="small"
            checked={rule.properties.loop === true}
            onChange={(v) =>
              onUpdate(rule.id, {
                properties: { ...rule.properties, loop: v },
              })
            }
          />
        </span>
      ),
    },
  ];
}

const RuleRow: React.FC<RuleRowProps> = ({ rule, onUpdate, onDelete, libraries }) => {
  return (
    <div
      style={{
        marginBottom: 12,
        padding: 12,
        border: '1px solid #d9d9d9',
        borderRadius: 4,
      }}
    >
      {/* 头部：名称 + 属性 + 删除 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          marginBottom: 8,
          gap: 8,
        }}
      >
        <Typography.Text
          editable={{
            onChange: (name) => onUpdate(rule.id, { name }),
          }}
          style={{ fontWeight: 600, flex: 1 }}
        >
          {rule.name}
        </Typography.Text>

        <Space size={4}>
          <Dropdown menu={{ items: buildPropertyMenuItems(rule, onUpdate) }} trigger={['click']}>
            <Button size="small" icon={<SettingOutlined />}>
              属性
            </Button>
          </Dropdown>

          <Popconfirm
            title="确认删除此规则？"
            onConfirm={() => onDelete(rule.id)}
            okText="删除"
            cancelText="取消"
          >
            <Button size="small" danger icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      </div>

      {/* 三个表达式区域 */}
      <ExpressionArea
        label="如果"
        type="condition"
        value={rule.conditionText}
        hasError={rule.conditionError}
        onChange={(v) => onUpdate(rule.id, { conditionText: v })}
        libraries={libraries}
        helpUrl="/admin/docs/rea-expression"
      />
      <ExpressionArea
        label="那么"
        type="action"
        value={rule.actionText}
        hasError={rule.actionError}
        onChange={(v) => onUpdate(rule.id, { actionText: v })}
        libraries={libraries}
      />
      <ExpressionArea
        label="否则"
        type="else"
        value={rule.elseText}
        hasError={rule.elseError}
        onChange={(v) => onUpdate(rule.id, { elseText: v })}
        libraries={libraries}
      />
    </div>
  );
};

export default RuleRow;
