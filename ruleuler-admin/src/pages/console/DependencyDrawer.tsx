import React, { useEffect, useRef, useState } from 'react';
import { Drawer, Tag, Empty, Spin, Typography } from 'antd';
import { useTranslation } from 'react-i18next';
import { analyzeDependencies } from '@/api/dependency';
import type { AnalysisResult, DependencyNode } from '@/api/dependency';

const { Text } = Typography;

interface DependencyDrawerProps {
  open: boolean;
  filePath: string | null;
  onClose: () => void;
  onNavigate?: (path: string) => void;
}

const REF_KIND_KEY: Record<string, string> = {
  library: 'console.libraryRef',
  file: 'console.fileRef',
  package_item: 'console.packageRef',
};

function getNodeName(path: string): string {
  return path.split('/').pop() ?? path;
}

function typeToColor(type: string): string {
  const map: Record<string, string> = {
    '变量库': '#531dab', '参数库': '#1677ff', '常量库': '#389e0d', '动作库': '#eb2f96',
    '规则集': '#d46b08', 'REA规则': '#8c4a1b', '脚本规则': '#595959',
    '决策表': '#08979c', '脚本决策表': '#08979c', '决策树': '#7c3aed',
    '决策流': '#1d39c4', '评分卡': '#c41d7f',
  };
  return map[type] ?? '#595959';
}

const DependencyDrawer: React.FC<DependencyDrawerProps> = ({ open, filePath, onClose, onNavigate }) => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const prevKey = useRef('');

  useEffect(() => {
    if (!open || !filePath) {
      setResult(null);
      setError(null);
      return;
    }
    const key = filePath;
    if (key === prevKey.current && result) return;
    prevKey.current = key;
    setLoading(true);
    setError(null);
    analyzeDependencies(filePath)
      .then((data) => setResult(data))
      .catch((e) => setError(e.message ?? t('console.analysisFailed')))
      .finally(() => setLoading(false));
  }, [open, filePath]);

  const handleClick = (path: string) => {
    onNavigate?.(path);
  };

  return (
    <Drawer
      title={filePath ? `${t('console.dependencyAnalysis')} — ${getNodeName(filePath)}` : t('console.dependencyAnalysis')}
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
    >
      {loading && <Spin tip={t('console.analyzing')} style={{ display: 'block', margin: '40px auto' }} />}
      {error && <Text type="danger">{error}</Text>}
      {!loading && !error && result && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
          <Section title={t('console.upstreamDeps', { type: result.target.type })} items={result.dependencies} empty={t('console.noUpstream')} onClick={handleClick} />
          <Section title={t('console.downstreamImpacts', { type: result.target.type })} items={result.referers} empty={t('console.noDownstream')} onClick={handleClick} />
          {result.affectedPackages.length > 0 && (
            <div>
              <Text strong>{t('console.affectedPackages')}</Text>
              <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 6 }}>
                {result.affectedPackages.map((pkg, i) => (
                  <div key={i} style={{ padding: '4px 8px', background: '#fff7e6', borderRadius: 4, borderLeft: '3px solid #fa8c16' }}>
                    <Text strong>{pkg.name || pkg.id}</Text>
                    <Text type="secondary" style={{ marginLeft: 8 }}>
                      via {getNodeName(pkg.viaFlow)}
                    </Text>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
      {!loading && !error && !result && <Empty description={t('console.selectFileFirst')} />}
    </Drawer>
  );
};

function Section({ title, items, empty, onClick }: {
  title: string;
  items: DependencyNode[];
  empty: string;
  onClick: (path: string) => void;
}) {
  const { t } = useTranslation();
  return (
    <div>
      <Text strong>{title}</Text>
      <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4 }}>
        {items.length === 0 && <Text type="secondary">{empty}</Text>}
        {items.map((node, i) => (
          <div
            key={i}
            style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '2px 4px', cursor: 'pointer', borderRadius: 4 }}
            onClick={() => onClick(node.path)}
            onMouseEnter={(e) => (e.currentTarget.style.background = '#f5f5f5')}
            onMouseLeave={(e) => (e.currentTarget.style.background = '')}
          >
            <Tag color={typeToColor(node.type)} style={{ margin: 0 }}>{node.type}</Tag>
            <Text copyable={{ text: node.path }}>{getNodeName(node.path)}</Text>
            {node.refKind && (
              <Text type="secondary" style={{ fontSize: 12 }}>{REF_KIND_KEY[node.refKind] ? t(REF_KIND_KEY[node.refKind]!) : node.refKind}</Text>
            )}
          </div>
        ))}
      </div>
    </div>
  );
}

export default DependencyDrawer;
