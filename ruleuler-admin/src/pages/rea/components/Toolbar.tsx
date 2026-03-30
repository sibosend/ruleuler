import React, { useState } from 'react';
import { Button, Space, Dropdown, Modal, message } from 'antd';
import {
  PlusOutlined,
  SaveOutlined,
  DownOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { parseCondition, parseAssignment } from '../lib/expressionParser';
import { saveFile } from '../api/reaApi';
import type { ProjectLibs } from '../api/reaApi';
import type { RuleState, LibraryState } from '../types';

// ─── Props ───

export interface ToolbarProps {
  file: string;
  rules: RuleState[];
  libraries: LibraryState;
  dirty: boolean;
  onAddRule: () => void;
  onRemoveLib: (
    type: 'variable' | 'constant' | 'parameter' | 'action',
    path: string,
  ) => void;
  onSaveComplete: () => void;
}

// ─── 库列表序列化 ───

const LIB_TAG_MAP: Record<keyof ProjectLibs, string> = {
  variable: 'import-variable-library',
  constant: 'import-constant-library',
  parameter: 'import-parameter-library',
  action: 'import-action-library',
};

/**
 * 将库路径列表序列化为 XML import 标签。
 * 属性测试 (Property 3) 也会使用此函数。
 */
export function serializeLibraries(paths: ProjectLibs): string {
  const lines: string[] = [];
  for (const type of Object.keys(LIB_TAG_MAP) as (keyof ProjectLibs)[]) {
    const tag = LIB_TAG_MAP[type];
    for (const p of paths[type]) {
      lines.push(`  <${tag} path="${escapeXml(p)}"/>`);
    }
  }
  return lines.join('\n');
}

function escapeXml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

// ─── 保存逻辑 ───

function buildRuleSetXml(
  rules: RuleState[],
  libraries: LibraryState,
): string {
  const libXml = serializeLibraries(libraries.paths);
  const ruleXmls: string[] = [];

  for (const rule of rules) {
    // 条件
    let condXml: string;
    try {
      condXml = parseCondition(rule.conditionText, libraries.data);
    } catch (e) {
      throw new Error(`规则 [${rule.name}] 的 [如果] 表达式语法错误：${e instanceof Error ? e.message : String(e)}`);
    }

    // 动作
    let actXml: string;
    try {
      actXml = parseAssignment(rule.actionText, libraries.data);
    } catch (e) {
      throw new Error(`规则 [${rule.name}] 的 [那么] 表达式语法错误：${e instanceof Error ? e.message : String(e)}`);
    }

    // 属性
    const attrs: string[] = [`name="${escapeXml(rule.name)}"`];
    for (const [k, v] of Object.entries(rule.properties)) {
      attrs.push(`${k}="${escapeXml(String(v))}"`);
    }

    let ruleBody = `  <rule ${attrs.join(' ')}>\n`;
    ruleBody += `    <if>\n  ${condXml}\n    </if>\n`;
    ruleBody += `    <then>\n  ${actXml}\n    </then>\n`;

    // else 可选
    if (rule.elseText.trim()) {
      let elseXml: string;
      try {
        elseXml = parseAssignment(rule.elseText, libraries.data);
      } catch (e) {
        throw new Error(`规则 [${rule.name}] 的 [否则] 表达式语法错误：${e instanceof Error ? e.message : String(e)}`);
      }
      ruleBody += `    <else>\n  ${elseXml}\n    </else>\n`;
    }

    ruleBody += '  </rule>';
    ruleXmls.push(ruleBody);
  }

  return [
    '<?xml version="1.0" encoding="UTF-8"?>',
    '<rule-set>',
    libXml,
    ...ruleXmls,
    '</rule-set>',
  ]
    .filter(Boolean)
    .join('\n');
}

// ─── 库 Dropdown 菜单构建 ───

type LibType = 'variable' | 'constant' | 'parameter' | 'action';

const LIB_LABELS: Record<LibType, string> = {
  variable: '变量库',
  constant: '常量库',
  parameter: '参数库',
  action: '动作库',
};

function buildLibMenuItems(
  type: LibType,
  paths: string[],
  onRemove: (type: LibType, path: string) => void,
): MenuProps['items'] {
  if (paths.length === 0) {
    return [{ key: 'empty', label: '(无)', disabled: true }];
  }
  return paths.map((p) => ({
    key: p,
    label: (
      <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
        <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {p}
        </span>
        <DeleteOutlined
          style={{ color: '#ff4d4f' }}
          onClick={(e) => {
            e.stopPropagation();
            onRemove(type, p);
          }}
        />
      </span>
    ),
  }));
}

// ─── 组件 ───

const Toolbar: React.FC<ToolbarProps> = ({
  file,
  rules,
  libraries,
  dirty,
  onAddRule,
  onRemoveLib,
  onSaveComplete,
}) => {
  const [saving, setSaving] = useState(false);

  const handleSave = async (newVersion: boolean) => {
    setSaving(true);
    try {
      const xml = buildRuleSetXml(rules, libraries);
      await saveFile(file, xml, newVersion);
      message.success(newVersion ? '已保存新版本' : '保存成功');
      onSaveComplete();
    } catch (e) {
      const msg = e instanceof Error ? e.message : '保存失败';
      Modal.error({ title: '保存失败', content: msg });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Space wrap style={{ marginBottom: 12, padding: '8px 0', borderBottom: '1px solid #eee' }}>
      <Button icon={<PlusOutlined />} onClick={onAddRule}>
        添加规则
      </Button>

      {(Object.keys(LIB_LABELS) as LibType[]).map((type) => (
        <Dropdown
          key={type}
          menu={{
            items: buildLibMenuItems(type, libraries.paths[type], onRemoveLib),
          }}
        >
          <Button>
            {LIB_LABELS[type]} ({libraries.paths[type].length}) <DownOutlined />
          </Button>
        </Dropdown>
      ))}

      <Button
        type="primary"
        icon={<SaveOutlined />}
        disabled={!dirty}
        loading={saving}
        onClick={() => handleSave(false)}
      >
        保存
      </Button>
      <Button
        icon={<SaveOutlined />}
        loading={saving}
        onClick={() => handleSave(true)}
      >
        保存新版本
      </Button>
    </Space>
  );
};

export default Toolbar;
