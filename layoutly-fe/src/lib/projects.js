import { apiRequest } from './api.js'
const call = async (path = '', options) => (await apiRequest('/projects' + path, options)).json
export const projectApi = {
  list: (q = '', framework = '') => call('?' + new URLSearchParams({ q, framework })),
  get: id => call('/' + id),
  create: body => call('', { method: 'POST', body }),
  update: (id, body) => call('/' + id, { method: 'PATCH', body }),
  remove: id => call('/' + id, { method: 'DELETE' }),
  duplicate: id => call('/' + id + '/duplicate', { method: 'POST' }),
  reopen: id => call('/' + id + '/reopen', { method: 'POST' }),
  saveVersion: (id, revisionId) => call('/' + id + '/versions', { method: 'POST', body: { revisionId } }),
  versions: id => call('/' + id + '/versions'),
  exports: id => call('/' + id + '/exports'),
}
