import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Project, ScanProgress, ScanSummary } from '../api/types'
import ScanProgressBar from '../components/ScanProgressBar'
import { SeverityBar } from '../components/SeverityBadge'

export default function ProjectDetailPage() {
  const { id = '' } = useParams()
  const nav = useNavigate()
  const [project, setProject] = useState<Project | null>(null)
  const [scans, setScans] = useState<ScanSummary[]>([])
  const [activeScan, setActiveScan] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const fileRef = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    const [p, s] = await Promise.all([api.project(id), api.scans(id, 0, 50)])
    setProject(p)
    setScans(s.content)
    const running = s.content.find((x) => x.status === 'RUNNING')
    if (running) setActiveScan(running.id)
  }, [id])

  useEffect(() => { load().catch(() => nav('/projects')) }, [load, nav])

  const upload = async (file: File) => {
    setError(null)
    try {
      const res = await api.upload(id, file)
      setActiveScan(res.scanId)        // 立刻订阅进度，此时 scan-service 可能还没收到事件
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Upload failed')
    }
  }

  const onDone = useCallback((p: ScanProgress) => {
    load()
    if (p.stage === 'COMPLETED') setTimeout(() => nav(`/scans/${p.scanId}`), 800)
  }, [load, nav])

  const remove = async () => {
    if (!confirm(`Delete project "${project?.name}" and all its scans?`)) return
    await api.deleteProject(id)
    nav('/projects')
  }

  if (!project) return <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <div>
          <div className="crumbs"><Link to="/projects">Projects</Link> / {project.name}</div>
          <h1>{project.name}</h1>
          {project.description && <p className="muted">{project.description}</p>}
        </div>
        <button className="btn danger" onClick={remove}>Delete</button>
      </div>

      <div
        className={`card dropzone ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => { e.preventDefault(); setDragging(false); const f = e.dataTransfer.files[0]; if (f) upload(f) }}
        onClick={() => fileRef.current?.click()}
      >
        <input ref={fileRef} type="file" hidden accept=".json,.xml,.txt" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload(f); e.target.value = '' }} />
        <strong>Drop a manifest here or click to upload</strong>
        <div className="small muted">
          <code>package-lock.json</code> · <code>pom.xml</code> (direct deps only) · <code>dependency-tree.txt</code> from <code>mvn dependency:tree -DoutputFile=dependency-tree.txt</code>
        </div>
        {error && <div className="error">{error}</div>}
      </div>

      {activeScan && scans.find((s) => s.id === activeScan)?.status !== 'COMPLETED' && (
        <div className="card">
          <ScanProgressBar scanId={activeScan} onDone={onDone} />
        </div>
      )}

      <h2>Scan history</h2>
      {scans.length === 0 ? <p className="muted">No scans yet.</p> : (
        <table className="table">
          <thead>
            <tr><th>When</th><th>Trigger</th><th>Status</th><th>Packages</th><th>Severity</th><th>Δ</th></tr>
          </thead>
          <tbody>
            {scans.map((s) => (
              <tr key={s.id} className="clickable" onClick={() => s.status === 'COMPLETED' && nav(`/scans/${s.id}`)}>
                <td>{new Date(s.startedAt).toLocaleString()}</td>
                <td><span className="chip">{s.trigger === 'MANUAL' ? s.manifestFileName ?? 'upload' : 'scheduled'}</span></td>
                <td><span className={`status status-${s.status.toLowerCase()}`}>{s.status}</span>{s.errorMessage && <div className="small error">{s.errorMessage}</div>}</td>
                <td>{s.status === 'COMPLETED' ? `${s.vulnerablePackages} / ${s.totalPackages}` : '—'}</td>
                <td>{s.status === 'COMPLETED' && <SeverityBar critical={s.critical} high={s.high} medium={s.medium} low={s.low} />}</td>
                <td className="small">
                  {s.newFindings > 0 && <span className="c-critical">+{s.newFindings} </span>}
                  {s.resolvedFindings > 0 && <span className="c-ok">−{s.resolvedFindings}</span>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
