import request from './request';

export interface FunctionJarItem {
  function_package: string;
  version: string;
  checksum: string;
  uploaded_by: string;
  uploaded_at: number;
}

export interface FunctionStatusItem {
  client_host: string;
  package_id: string;
  function_package: string;
  expected_version: string;
  actual_version: string;
  status: string;
  reported_at: number;
}

export async function listFunctionJars(project: string) {
  const res = await request.get(`/api/projects/${encodeURIComponent(project)}/function-jars`);
  return res.data.data as FunctionJarItem[];
}

export async function uploadFunctionJar(project: string, file: File) {
  const form = new FormData();
  form.append('file', file);
  const res = await request.post(`/api/projects/${encodeURIComponent(project)}/function-jars`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return res.data.data;
}

export async function listFunctionStatus(project: string) {
  const res = await request.get(`/api/projects/${encodeURIComponent(project)}/function-jars/status`);
  return res.data.data as FunctionStatusItem[];
}
