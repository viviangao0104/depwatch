import { FormEvent, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import type { Project, ScanSummary } from '../api/types'
import { SeverityBar } from '../components/SeverityBadge'

export default function ProjectsPage() {
  const [projects, setProjects] = useState<Project[]>([])
  const [latest, setLatest] = useState<Record<string, ScanSummary>>({})
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      const [ps, scans] = await Promise.all([api.projects(), api.scans(undefined, 0, 100)])
      setProjects(ps)
      // Latest scan per project for the overview (scans are already sorted newest-first)
      const map: Record<string, ScanSummary> = {}
      for (const s of scans.content) if (!map[s.projectId]) map[s.projectId] = s
      setLatest(map)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { load() }, [])

  const create = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      await api.createProject(name, desc)
      setName(''); setDesc('')
      await load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed')
    }
  }

  return (
    <>
      <div className="page-head">
        <h1>Projects</h1>
      </div>

      <form className="card row-form" onSubmit={create}>
        <input placeholder="Project name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        <input placeholder="Description (optional)" value={desc} onChange={(e) => setDesc(e.target.value)} maxLength={500} />
        <button className="btn primary">New project</button>
        {error && <span className="error">{error}</span>}
      </form>

      {loading ? <p className="muted">Loading…</p> : projects.length === 0 ? (
        <div className="card empty">
          <p>No projects yet. Create one, then upload a <code>package-lock.json</code> or <code>mvn dependency:tree</code> output.</p>
        </div>
      ) : (
        <div className="grid">
          {projects.map((p) => {
            const s = latest[p.id]
            return (
              <Link key={p.id} to={`/projects/${p.id}`} className="card project-card">
                <h3>{p.name}</h3>
                {p.description && <p className="muted small">{p.description}</p>}
                {s ? (
                  <>
                    <SeverityBar critical={s.critical} high={s.high} medium={s.medium} low={s.low} />
                    <div className="small muted">
                      {s.status === 'RUNNING' ? 'Scan running…' :
                        `${s.vulnerablePackages}/${s.totalPackages} packages vulnerable · ${new Date(s.startedAt).toLocaleDateString()}`}
                      {s.trigger === 'SCHEDULED' && ' · scheduled'}
                    </div>
                  </>
                ) : <div className="small muted">Not scanned yet</div>}
              </Link>
            )
          })}
        </div>
      )}
    </>
  )
}
