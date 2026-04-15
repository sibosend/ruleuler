import request from './request';

export interface DependencyNode {
  path: string;
  type: string;
  refKind: string | null;
}

export interface AffectedPackage {
  id: string;
  name: string;
  viaFlow: string;
}

export interface AnalysisResult {
  target: DependencyNode;
  dependencies: DependencyNode[];
  referers: DependencyNode[];
  affectedPackages: AffectedPackage[];
}

export async function analyzeDependencies(path: string): Promise<AnalysisResult> {
  const res = await request.get('/api/dependencies/analyze', { params: { path } });
  return res.data.data as AnalysisResult;
}
