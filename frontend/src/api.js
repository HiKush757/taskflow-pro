import axios from 'axios';

const client = axios.create({ baseURL: '/api' });

export const api = {
  listTasks: () => client.get('/tasks').then(r => r.data),
  createTask: (payload) => client.post('/tasks', payload).then(r => r.data),
  moveTask: (id, status, position) =>
    client.patch(`/tasks/${id}/move`, { status, position }).then(r => r.data),
  updateSchedule: (id, payload) =>
    client.patch(`/tasks/${id}/schedule`, payload).then(r => r.data),
  addDependency: (taskId, prerequisiteId) =>
    client.post(`/tasks/${taskId}/dependencies`, { prerequisiteId }).then(r => r.data),
  removeDependency: (taskId, prerequisiteId) =>
    client.delete(`/tasks/${taskId}/dependencies/${prerequisiteId}`),
  suggestDependencies: (taskId) =>
    client.post(`/tasks/${taskId}/suggest-dependencies`).then(r => r.data),
  acceptSuggestion: (suggestionId) =>
    client.post(`/suggestions/${suggestionId}/accept`).then(r => r.data),
  rejectSuggestion: (suggestionId) =>
    client.post(`/suggestions/${suggestionId}/reject`).then(r => r.data),
  criticalPath: () => client.get('/tasks/critical-path').then(r => r.data),
};

/** Every write call can be rejected by the engine (409 for a cycle or a
 * Blocked-forward move). Callers show err.response.data.error to the user
 * rather than silently swallowing it. */
export function errorMessage(err) {
  return err?.response?.data?.error || err.message || 'Something went wrong.';
}
