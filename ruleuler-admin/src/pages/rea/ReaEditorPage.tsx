import React, { useEffect, useState, useCallback, useRef } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Spin, Alert, Modal, message } from 'antd';
import { useTranslation } from 'react-i18next';
import { loadProjectLibs, loadXml, loadRawXml } from './api/reaApi';
import { printCondition, printAssignment } from './lib/expressionPrinter';
import { createDefaultRule } from './lib/ruleUtils';
import type { LibraryData } from './lib/expressionParser';
import type { ProjectLibs } from './api/reaApi';
import type { RuleState, LibraryState } from './types';
import Toolbar from './components/Toolbar';
import RuleRow from './components/RuleRow';

// ─── XML 解析辅助 ───

function parseRulesFromXml(xmlText: string): RuleState[] {
  const parser = new DOMParser();
  const doc = parser.parseFromString(xmlText, 'text/xml');
  const errorNode = doc.querySelector('parsererror');
  if (errorNode) throw new Error('XML 解析失败');

  const ruleEls = doc.querySelectorAll('rule');
  const rules: RuleState[] = [];

  ruleEls.forEach((el, idx) => {
    const name = el.getAttribute('name') || `rule${idx + 1}`;

    // 提取属性
    const properties: Record<string, string | boolean | number> = {};
    const attrNames = ['salience', 'effective-date', 'expires-date', 'enabled', 'debug', 'shadow', 'loop'];
    for (const attr of attrNames) {
      const val = el.getAttribute(attr);
      if (val != null) {
        if (attr === 'enabled' || attr === 'debug' || attr === 'shadow' || attr === 'loop') {
          properties[attr] = val === 'true';
        } else if (attr === 'salience') {
          properties[attr] = Number(val) || 0;
        } else {
          properties[attr] = val;
        }
      }
    }

    // 提取 if/then/else 的 outerHTML 传给 printer
    const ifEl = el.querySelector(':scope > if');
    const thenEl = el.querySelector(':scope > then');
    const elseEl = el.querySelector(':scope > else');

    const condResult = ifEl ? printCondition(ifEl.outerHTML) : { text: '', hasError: false };
    const actResult = thenEl ? printAssignment(thenEl.outerHTML) : { text: '', hasError: false };
    const elseResult = elseEl ? printAssignment(elseEl.outerHTML) : { text: '', hasError: false };

    rules.push({
      id: `rule-${idx}-${Date.now()}`,
      name,
      properties,
      conditionText: condResult.text,
      actionText: actResult.text,
      elseText: elseResult.text,
      conditionError: condResult.hasError,
      actionError: actResult.hasError,
      elseError: elseResult.hasError,
    });
  });

  return rules;
}

/** 从 loadXml 返回的 JSON 数组中提取库数据 */
function buildLibraryData(
  paths: ProjectLibs,
  libJsonArray: unknown[],
): LibraryData {
  // loadXml 按传入顺序返回：variable[], constant[], parameter[], action[]
  // 我们按 variable → constant → parameter → action 的顺序拼接路径
  const allPaths = [
    ...paths.variable,
    ...paths.constant,
    ...paths.parameter,
    ...paths.action,
  ];

  const variableCount = paths.variable.length;
  const parameterStart = variableCount + paths.constant.length;

  const variables: LibraryData['variables'] = [];
  const parameters: LibraryData['parameters'] = [];

  // 解析变量库 JSON
  for (let i = 0; i < variableCount; i++) {
    const raw = libJsonArray[i] as Record<string, unknown> | undefined;
    if (!raw) continue;
    // 变量库 JSON 结构: { name, variables: [{name, label, type}] } 或 categories 数组
    if (Array.isArray(raw)) {
      for (const cat of raw) {
        variables.push(cat as LibraryData['variables'][0]);
      }
    } else if (raw.categories && Array.isArray(raw.categories)) {
      for (const cat of raw.categories as LibraryData['variables']) {
        variables.push(cat);
      }
    } else if (raw.name) {
      variables.push(raw as unknown as LibraryData['variables'][0]);
    }
  }

  // 解析参数库 JSON
  for (let i = parameterStart; i < parameterStart + paths.parameter.length; i++) {
    const raw = libJsonArray[i] as Record<string, unknown> | undefined;
    if (!raw) continue;
    if (Array.isArray(raw)) {
      for (const p of raw) {
        parameters.push(p as LibraryData['parameters'][0]);
      }
    } else if (raw.parameters && Array.isArray(raw.parameters)) {
      for (const p of raw.parameters as LibraryData['parameters']) {
        parameters.push(p);
      }
    } else if (raw.name) {
      parameters.push(raw as unknown as LibraryData['parameters'][0]);
    }
  }

  void allPaths; // paths used for ordering reference
  return { variables, parameters };
}

// ─── 主组件 ───

const ReaEditorPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const file = searchParams.get('file');
  const project = searchParams.get('project');
  const { t } = useTranslation();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rules, setRules] = useState<RuleState[]>([]);
  const [libraries, setLibraries] = useState<LibraryState | null>(null);
  const [dirty, setDirty] = useState(false);

  const handleAddRule = () => {
    setRules(prev => [...prev, createDefaultRule()]);
    setDirty(true);
  };

  const handleUpdateRule = (id: string, updates: Partial<RuleState>) => {
    setRules(prev => prev.map(r => r.id === id ? { ...r, ...updates } : r));
    setDirty(true);
  };

  const handleDeleteRule = (id: string) => {
    setRules(prev => prev.filter(r => r.id !== id));
    setDirty(true);
  };

  const handleRemoveLib = (type: 'variable' | 'constant' | 'parameter' | 'action', path: string) => {
    if (!libraries) return;
    setLibraries({
      ...libraries,
      paths: { ...libraries.paths, [type]: libraries.paths[type].filter(p => p !== path) },
    });
    setDirty(true);
  };

  const initialize = useCallback(async () => {
    if (!file || !project) {
      setError(t('rea.missingParams'));
      setLoading(false);
      return;
    }

    try {
      // 1. 加载项目库文件路径
      const paths = await loadProjectLibs(project);

      // 2. 批量加载库数据
      const allLibPaths = [
        ...paths.variable,
        ...paths.constant,
        ...paths.parameter,
        ...paths.action,
      ];

      let libData: LibraryData = { variables: [], parameters: [] };
      if (allLibPaths.length > 0) {
        try {
          const libJsonArray = await loadXml(allLibPaths.join(';'));
          libData = buildLibraryData(paths, libJsonArray);
        } catch (e) {
          message.error(t('rea.libLoadFailed'));
          console.error('loadXml libs failed:', e);
        }
      }

      setLibraries({ paths, data: libData });

      // 3. 加载决策集 XML
      const xmlContent = await loadRawXml(file);
      if (!xmlContent || xmlContent.trim().length === 0) {
        setRules([]);
        setLoading(false);
        return;
      }
      const parsed = parseRulesFromXml(xmlContent);
      setRules(parsed);
    } catch (e) {
      const msg = e instanceof Error ? e.message : t('rea.initFailed');
      Modal.error({ title: t('rea.loadFailed'), content: msg });
      setError(msg);
    } finally {
      setLoading(false);
    }
  }, [file, project, t]);

  const fetched = useRef(false);
  useEffect(() => {
    if (fetched.current) return;
    fetched.current = true;
    initialize();
  }, [initialize]);

  // ─── 渲染 ───

  if (!file || !project) {
    return (
      <div style={{ padding: 24 }}>
        <Alert type="error" message={t('rea.missingParams')} description={t('rea.missingParamsDesc')} />
      </div>
    );
  }

  if (loading) {
    return (
      <div style={{ padding: 24, textAlign: 'center' }}>
        <Spin size="large" tip={t('common.loading')} />
      </div>
    );
  }

  if (error) {
    return (
      <div style={{ padding: 24 }}>
        <Alert type="error" message={t('rea.loadFailed')} description={error} />
      </div>
    );
  }

  return (
    <div className="rea-editor" style={{ padding: '8px 16px' }}>
      {libraries && (
        <Toolbar
          file={file}
          rules={rules}
          libraries={libraries}
          dirty={dirty}
          onAddRule={handleAddRule}
          onRemoveLib={handleRemoveLib}
          onSaveComplete={() => setDirty(false)}
        />
      )}

      {rules.length === 0 ? (
        <div style={{ color: '#999', padding: 24, textAlign: 'center' }}>
          {t('rea.noRules')}
        </div>
      ) : (
        rules.map((rule) => (
          <RuleRow
            key={rule.id}
            rule={rule}
            onUpdate={handleUpdateRule}
            onDelete={handleDeleteRule}
            libraries={libraries?.data ?? { variables: [], parameters: [] }}
          />
        ))
      )}
    </div>
  );
};

export default ReaEditorPage;
