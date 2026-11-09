import { useEffect, useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import type { DependencyRow, Finding, ScanSummary, Severity, VulnerabilityDetail } from '../api/types'
import SeverityBadge, { SeverityBar } from '../components/SeverityBadge'

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNKNOWN']

export default function ScanDetailPage() {
  const { id = '' } = useParams()
  const [scan, setScan] = useState<ScanSummary | null>(null)
  const [findings, setFindings] = useState<Finding[]>([])
  const [deps, setDeps] = useState<DependencyRow[]>([])
  const [tab, setTab] = useState<'findings' | 'deps'>('findings')
  const [minSev, setMinSev] = useState<Severity | ''>('')
  const [onlyNew, setOnlyNew] = useState(false)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Finding | null>(null)

  useEffect(() => {
    api.scan(id).then(setScan)
    api.findings(id).then(setFindings)
    api.dependencies(id).then(setDeps)
  }, [id])

  const visible = useMemo(() => {
    const min = minSev ? SEVERITIES.indexOf(minSev) : SEVERITIES.length
    return findings.filter((f) =>
      (!minSev || SEVERITIES.indexOf(f.severity) <= min) &&
      (!onlyNew || f.isNew) &&
      (!search || f.packageName.toLowerCase().includes(search.toLowerCase()) || f.vulnId.toLowerCase().includes(search.toLowerCase())))
  }, [findings, minSev, onlyNew, search])

  if (!scan) return <p className="muted">Loading…</p>

  return (
    <>
      <div className="page-head">
        <div>
          <div className="crumbs"><Link to="/projects">Projects</Link> / <Link to={`/projects/${scan.projectId}`}>{scan.projectName}</Link> / scan</div>
          <h1>Scan results</h1>
          <div className="muted small">
            {new Date(scan.startedAt).toLocaleString()} · {scan.trigger.toLowerCase()} · {scan.manifestFileName}
          </div>
        </div>
      </div>

      <div className="stats">
        <div className="stat"><div className="stat-n">{scan.totalPackages}</div><div className="stat-l">packages ({scan.directPackages} direct)</div></div>
        <div className="stat"><div className="stat-n">{scan.vulnerablePackages}</div><div className="stat-l">vulnerable</div></div>
        <div className="stat"><div className="stat-n">{findings.length}</div><div className="stat-l">findings</div></div>
        <div className="stat"><div className="stat-n c-critical">+{scan.newFindings}</div><div className="stat-l">new since last scan</div></div>
        <div className="stat"><div className="stat-n c-ok">−{scan.resolvedFindings}</div><div className="stat-l">resolved</div></div>
        <div className="stat wide"><SeverityBar critical={scan.critical} high={scan.high} medium={scan.medium} low={scan.low} /></div>
      </div>

      <div className="tabs">
        <button className={tab === 'findings' ? 'active' : ''} onClick={() => setTab('findings')}>Findings ({findings.length})</button>
        <button className={tab === 'deps' ? 'active' : ''} onClick={() => setTab('deps')}>Dependency graph ({deps.length})</button>
      </div>

      {tab === 'findings' && (
        <>
          <div className="toolbar">
            <input placeholder="Search package or CVE/GHSA…" value={search} onChange={(e) => setSearch(e.target.value)} />
            <select value={minSev} onChange={(e) => setMinSev(e.target.value as Severity | '')}>
              <option value="">All severities</option>
              {SEVERITIES.map((s) => <option key={s} value={s}>≥ {s}</option>)}
            </select>
            <label className="check"><input type="checkbox" checked={onlyNew} onChange={(e) => setOnlyNew(e.target.checked)} /> New only</label>
          </div>
          {visible.length === 0 ? <div className="card empty">🎉 Nothing here.</div> : (
            <table className="table">
              <thead><tr><th>Severity</th><th>Package</th><th>Vulnerability</th><th>Fixed in</th><th></th></tr></thead>
              <tbody>
                {visible.map((f) => (
                  <tr key={f.nodeId + f.vulnId} className="clickable" onClick={() => setSelected(f)}>
                    <td><SeverityBadge severity={f.severity} score={f.cvssScore} /></td>
                    <td>
                      <code>{f.packageName}</code> <span className="muted">@{f.version}</span>
                      <div className="small muted">{f.direct ? 'direct' : 'transitive'} · {f.ecosystem}</div>
                    </td>
                    <td>
                      <div><code>{f.vulnId}</code> {f.isNew && <span className="chip new">NEW</span>}</div>
                      <div className="small muted clamp">{f.summary}</div>
                    </td>
                    <td>{f.fixedVersion ? <code className="c-ok">{f.fixedVersion}</code> : <span className="muted small">no fix</span>}</td>
                    <td className="muted">›</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </>
      )}

      {tab === 'deps' && <DependencyTable scanId={id} deps={deps} />}

      {selected && <FindingDrawer scanId={id} finding={selected} onClose={() => setSelected(null)} />}
    </>
  )
}

function DependencyTable({ scanId, deps }: { scanId: string; deps: DependencyRow[] }) {
  const [q, setQ] = useState('')
  const [vulnOnly, setVulnOnly] = useState(false)
  const [path, setPath] = useState<{ nodeId: string; path: string[] } | null>(null)
  const rows = deps.filter((d) => (!vulnOnly || d.findingCount > 0) && (!q || d.name.toLowerCase().includes(q.toLowerCase())))

  const showPath = async (nodeId: string) => setPath(await api.path(scanId, nodeId))

  return (
    <>
      <div className="toolbar">
        <input placeholder="Filter by name…" value={q} onChange={(e) => setQ(e.target.value)} />
        <label className="check"><input type="checkbox" checked={vulnOnly} onChange={(e) => setVulnOnly(e.target.checked)} /> Vulnerable only</label>
        <span className="muted small">{rows.length} shown</span>
      </div>
      {path && (
        <div className="card path-card">
          <strong>How did <code>{path.nodeId.split(':').slice(1).join(':')}</code> get here?</strong>
          <div className="path">
            <span className="chip">your project</span>
            {path.path.map((n) => <span key={n}>→ <code>{n.split(':').slice(1).join(':')}</code></span>)}
          </div>
          <button className="btn-link" onClick={() => setPath(null)}>close</button>
        </div>
      )}
      <table className="table">
        <thead><tr><th>Package</th><th>Version</th><th>Depth</th><th>Findings</th><th></th></tr></thead>
        <tbody>
          {rows.slice(0, 500).map((d) => (
            <tr key={d.nodeId}>
              <td><code>{d.name}</code> <span className="small muted">{d.ecosystem}</span></td>
              <td>{d.version}</td>
              <td>{d.direct ? <span className="chip">direct</span> : <span className="muted">{d.depth}</span>}</td>
              <td>{d.findingCount > 0 ? <b className="c-critical">{d.findingCount}</b> : <span className="muted">0</span>}</td>
              <td>{!d.direct && <button className="btn-link" onClick={() => showPath(d.nodeId)}>why?</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
      {rows.length > 500 && <p className="muted small">Showing first 500 — refine the filter.</p>}
    </>
  )
}

function FindingDrawer({ scanId, finding, onClose }: { scanId: string; finding: Finding; onClose: () => void }) {
  const [vuln, setVuln] = useState<VulnerabilityDetail | null>(null)
  const [path, setPath] = useState<string[] | null>(null)

  useEffect(() => {
    setVuln(null); setPath(null)
    api.vulnerability(finding.vulnId).then(setVuln)
    if (!finding.direct) api.path(scanId, finding.nodeId).then((p) => setPath(p.path))
  }, [finding, scanId])

  return (
    <div className="drawer-backdrop" onClick={onClose}>
      <aside className="drawer" onClick={(e) => e.stopPropagation()}>
        <button className="drawer-close" onClick={onClose}>×</button>
        <SeverityBadge severity={finding.severity} score={finding.cvssScore} />
        <h2><code>{finding.vulnId}</code></h2>
        <p className="muted">{finding.packageName}@{finding.version} · {finding.ecosystem}</p>

        <section>
          <h4>Remediation</h4>
          {finding.fixedVersion
            ? <p>Upgrade <code>{finding.packageName}</code> to <code className="c-ok">{finding.fixedVersion}</code> or later.</p>
            : <p className="muted">No fixed version published yet. Consider replacing the package or applying mitigations from the references.</p>}
          {path && path.length > 0 && (
            <>
              <p className="small muted">This is a transitive dependency. Introduced via:</p>
              <div className="path small">
                <span className="chip">your project</span>
                {path.map((n) => <span key={n}>→ <code>{n.split(':').slice(1).join(':')}</code></span>)}
              </div>
              <p className="small muted">Upgrade the first package in this chain, or add an override/resolution.</p>
            </>
          )}
        </section>

        {vuln ? (
          <>
            <section>
              <h4>Summary</h4>
              <p>{vuln.summary ?? '—'}</p>
              {vuln.cvssVector && <p className="small muted mono">{vuln.cvssVector}</p>}
              {vuln.aliases.length > 0 && <p className="small">Aliases: {vuln.aliases.map((a) => <code key={a}>{a} </code>)}</p>}
              <p className="small muted">
                Published {vuln.publishedAt ? new Date(vuln.publishedAt).toLocaleDateString() : '?'} ·
                Modified {vuln.modifiedAt ? new Date(vuln.modifiedAt).toLocaleDateString() : '?'}
              </p>
            </section>
            {vuln.details && (
              <section>
                <h4>Details</h4>
                <pre className="details">{vuln.details}</pre>
              </section>
            )}
            <section>
              <h4>References</h4>
              <ul className="refs">
                <li><a href={`https://osv.dev/vulnerability/${vuln.id}`} target="_blank" rel="noreferrer">osv.dev/{vuln.id}</a></li>
                {vuln.references.slice(0, 12).map((r) => (
                  <li key={r.url}><span className="chip">{r.type}</span> <a href={r.url} target="_blank" rel="noreferrer">{r.url}</a></li>
                ))}
              </ul>
            </section>
          </>
        ) : <p className="muted">Loading details…</p>}
      </aside>
    </div>
  )
}
