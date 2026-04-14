import request from './request';

export interface AuditLogRecord {
  id: number;
  action: string;
  target_type: string;
  target_id: number | null;
  target_path: string | null;
  project: string | null;
  operator: string;
  detail: Record<string, unknown> | null;
  ip: string | null;
  created_at: number;
}

export async function fetchAuditLogs(params: {
  action?: string;
  targetType?: string;
  operator?: string;
  project?: string;
  startTime?: number;
  endTime?: number;
  page?: number;
  pageSize?: number;
}) {
  const res = await request.get('/api/audit-logs', { params });
  return res.data.data;
}
