export type Severity = 'UNKNOWN' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'

export interface UserInfo {
  id: string
  email: string
  displayName: string
}

export interface AuthResponse {
  accessToken: string
  expiresInSeconds: number
  user: UserInfo
}

export interface Project {
  id: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface ManifestInfo {
  id: string
  fileName: string
  manifestType: string
  sizeBytes: number
  scanId: string
  uploadedAt: string
}

export interface UploadResponse {
  manifestId: string
  scanId: string
  manifestType: string
}

export interface ScanSummary {
  id: string
  projectId: string
  projectName: string
  trigger: 'MANUAL' | 'SCHEDULED'
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  manifestFileName: string | null
  totalPackages: number
  directPackages: number
  vulnerablePackages: number
  critical: number
  high: number
  medium: number
  low: number
  unknown: number
  newFindings: number
  resolvedFindings: number
  errorMessage: string | null
  startedAt: string
  completedAt: string | null
}

export interface Finding {
  nodeId: string
  ecosystem: string
  packageName: string
  version: string
  direct: boolean
  vulnId: string
  summary: string | null
  severity: Severity
  cvssScore: number | null
  fixedVersion: string | null
  isNew: boolean
  aliases: string[]
}

export interface DependencyRow {
  nodeId: string
  ecosystem: string
  name: string
  version: string
  direct: boolean
  depth: number
  findingCount: number
}

export interface VulnerabilityDetail {
  id: string
  summary: string | null
  details: string | null
  severity: Severity
  cvssScore: number | null
  cvssVector: string | null
  aliases: string[]
  references: { type: string; url: string }[]
  publishedAt: string | null
  modifiedAt: string | null
}

export interface ScanProgress {
  scanId: string
  stage: 'QUEUED' | 'PARSING' | 'QUERYING' | 'ENRICHING' | 'DIFFING' | 'COMPLETED' | 'FAILED'
  current: number
  total: number
  message: string | null
  occurredAt: string
}

export interface Alert {
  id: string
  projectId: string
  projectName: string | null
  scanId: string
  vulnId: string | null
  severity: Severity
  kind: 'NEW_VULNERABILITY' | 'SCAN_COMPLETED' | 'SCAN_FAILED'
  title: string
  message: string | null
  createdAt: string
  readAt: string | null
}

export interface AlertRule {
  minSeverity: Severity
  inApp: boolean
  webhookUrl: string | null
  notifyOnScanComplete: boolean
}

export interface Page<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}
