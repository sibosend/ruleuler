import request from './request';

export function loadProjects() {
  return request.get('/api/projects');
}

export function createProject(name: string, storageType = 'db') {
  return request.post('/api/projects', { name, storageType });
}

export function deleteProject(name: string) {
  return request.delete(`/api/projects/${encodeURIComponent(name)}`);
}

export function exportProject(name: string) {
  return request.get(`/api/projects/${encodeURIComponent(name)}/export`, {
    responseType: 'blob',
  });
}

export function importProject(file: File, overwrite = true) {
  const form = new FormData();
  form.append('file', file);
  form.append('overwrite', String(overwrite));
  return request.post('/api/projects/import', form);
}

export function checkProjectExist(name: string) {
  return request.get(`/api/projects/${encodeURIComponent(name)}/exists`);
}
