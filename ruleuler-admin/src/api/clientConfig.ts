import request from './request';

export interface ClientConfigItem {
  name: string;
  client: string;
}

export function loadClientConfig(project: string) {
  return request.post<ClientConfigItem[]>(
    `/urule/clientconfig/loadData?project=${encodeURIComponent(project)}`,
  );
}

export function saveClientConfig(project: string, content: string) {
  return request.post(
    '/urule/clientconfig/save',
    `project=${encodeURIComponent(project)}&content=${encodeURIComponent(content)}`,
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } },
  );
}

export function serializeConfigs(items: ClientConfigItem[]): string {
  const inner = items
    .map((i) => `  <item name="${i.name}" client="${i.client}"/>`)
    .join('\n');
  return `<items>\n${inner}\n</items>`;
}
