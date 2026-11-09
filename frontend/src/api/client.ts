import type {
  Alert, AlertRule, AuthResponse, DependencyRow, Finding, ManifestInfo, Page, Project,
  ScanSummary, Severity, UploadResponse, UserInfo, VulnerabilityDetail,
} from './types'

const TOKEN_KEY = 'depwatch.token'

export const auth = {
  token: () => localStorage.getItem(TOKEN_KEY),
  set: (t: string) => localStorage.setItem(TOKEN_KEY, t),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export class ApiError extends Error {
  constructor(public status: number, message: string, public fieldErrors?: Record<string, string>) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  const token = auth.token()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json')

  const res = await fetch(path, { ...init, headers })
  if (res.status === 401) {
    auth.clear()
    if (!location.pathname.startsWith('/login')) location.href = '/login'
  }
  if (!res.ok) {
    let body: { message?: string; fieldErrors?: Record<string, string> } = {}
    try { body = await res.json() } catch { /* ignore */ }
    throw new ApiError(res.status, body.message ?? res.statusText, body.fieldErrors)
  }
  if (res.status === 204) return undefined as T
  return res.json() as Promise<T>
}

export const api = {
  // auth (project-service)
  register: (email: string, password: string, displayName: string) =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify({ email, password, displayName }) }),
  login: (email: string, password: string) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }),
  me: () => request<UserInfo>('/api/auth/me'),

  // projects
  projects: () => request<Project[]>('/api/projects'),
  project: (id: string) => request<Project>(`/api/projects/${id}`),
  createProject: (name: string, description: string) =>
    request<Project>('/api/projects', { method: 'POST', body: JSON.stringify({ name, description }) }),
  deleteProject: (id: string) => request<void>(`/api/projects/${id}`, { method: 'DELETE' }),
  manifests: (projectId: string) => request<ManifestInfo[]>(`/api/projects/${projectId}/manifests`),
  upload: (projectId: string, file: File) => {
    const fd = new FormData()
    fd.append('file', file)
    return request<UploadResponse>(`/api/projects/${projectId}/manifests`, { method: 'POST', body: fd })
  },

  // scans (scan-service)
  scans: (projectId?: string, page = 0, size = 20) =>
    request<Page<ScanSummary>>(`/api/scans?${projectId ? `projectId=${projectId}&` : ''}page=${page}&size=${size}`),
  scan: (id: string) => request<ScanSummary>(`/api/scans/${id}`),
  findings: (id: string, minSeverity?: Severity, onlyNew?: boolean) => {
    const q = new URLSearchParams()
    if (minSeverity) q.set('minSeverity', minSeverity)
    if (onlyNew) q.set('onlyNew', 'true')
    return request<Finding[]>(`/api/scans/${id}/findings?${q}`)
  },
  dependencies: (id: string) => request<DependencyRow[]>(`/api/scans/${id}/dependencies`),
  path: (id: string, nodeId: string) =>
    request<{ nodeId: string; path: string[] }>(`/api/scans/${id}/path?nodeId=${encodeURIComponent(nodeId)}`),
  vulnerability: (id: string) => request<VulnerabilityDetail>(`/api/vulns/${id}`),

  // alerts (alert-service)
  alerts: (unreadOnly = false, page = 0) => request<Page<Alert>>(`/api/alerts?unreadOnly=${unreadOnly}&page=${page}`),
  unreadCount: () => request<{ unread: number }>('/api/alerts/unread-count'),
  markRead: (id: string) => request<Alert>(`/api/alerts/${id}/read`, { method: 'POST' }),
  markAllRead: () => request<{ updated: number }>('/api/alerts/read-all', { method: 'POST' }),
  rule: () => request<AlertRule>('/api/alerts/rules'),
  updateRule: (rule: AlertRule) => request<AlertRule>('/api/alerts/rules', { method: 'PUT', body: JSON.stringify(rule) }),
}
