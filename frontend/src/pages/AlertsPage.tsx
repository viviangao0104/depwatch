import { FormEvent, useEffect, useState } from 'react'
import { Link, useOutletContext } from 'react-router-dom'
import { api } from '../api/client'
import type { Alert, AlertRule, Severity } from '../api/types'
import SeverityBadge from '../components/SeverityBadge'

export default function AlertsPage() {
  const { setUnread } = useOutletContext<{ setUnread: (n: number) => void }>()
  const [alerts, setAlerts] = useState<Alert[]>([])
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [rule, setRule] = useState<AlertRule | null>(null)
  const [saved, setSaved] = useState(false)

  const load = () => api.alerts(unreadOnly).then((p) => setAlerts(p.content))
  useEffect(() => { load() }, [unreadOnly]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { api.rule().then(setRule) }, [])

  const read = async (a: Alert) => {
    if (a.readAt) return
    await api.markRead(a.id)
    api.unreadCount().then((r) => setUnread(r.unread))
    load()
  }

  const readAll = async () => {
    await api.markAllRead()
    setUnread(0)
    load()
  }

  const saveRule = async (e: FormEvent) => {
    e.preventDefault()
    if (!rule) return
    setRule(await api.updateRule(rule))
    setSaved(true)
    setTimeout(() => setSaved(false), 2000)
  }

  return (
    <>
      <div className="page-head">
        <h1>Alerts</h1>
        <div className="toolbar">
          <label className="check"><input type="checkbox" checked={unreadOnly} onChange={(e) => setUnreadOnly(e.target.checked)} /> Unread only</label>
          <button className="btn" onClick={readAll}>Mark all read</button>
        </div>
      </div>

      <div className="two-col">
        <div>
          {alerts.length === 0 ? <div className="card empty">No alerts.</div> : alerts.map((a) => (
            <div key={a.id} className={`card alert ${a.readAt ? 'read' : 'unread'}`} onClick={() => read(a)}>
              <div className="alert-head">
                <SeverityBadge severity={a.severity} />
                <span className="chip">{a.kind.replace('_', ' ').toLowerCase()}</span>
                <span className="muted small">{new Date(a.createdAt).toLocaleString()}</span>
              </div>
              <strong>{a.title}</strong>
              {a.message && <div className="small muted pre">{a.message}</div>}
              <div className="small">
                <Link to={`/scans/${a.scanId}`}>View scan</Link>
                {a.projectName && <span className="muted"> · {a.projectName}</span>}
                {a.vulnId && <> · <a href={`https://osv.dev/vulnerability/${a.vulnId}`} target="_blank" rel="noreferrer">{a.vulnId}</a></>}
              </div>
            </div>
          ))}
        </div>

        {rule && (
          <form className="card rule-form" onSubmit={saveRule}>
            <h3>Notification rules</h3>
            <label>Minimum severity
              <select value={rule.minSeverity} onChange={(e) => setRule({ ...rule, minSeverity: e.target.value as Severity })}>
                {(['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'] as Severity[]).map((s) => <option key={s}>{s}</option>)}
              </select>
            </label>
            <label className="check"><input type="checkbox" checked={rule.inApp} onChange={(e) => setRule({ ...rule, inApp: e.target.checked })} /> In-app notifications</label>
            <label className="check"><input type="checkbox" checked={rule.notifyOnScanComplete} onChange={(e) => setRule({ ...rule, notifyOnScanComplete: e.target.checked })} /> Notify when a scan completes</label>
            <label>Slack-compatible webhook URL
              <input placeholder="https://hooks.slack.com/services/…" value={rule.webhookUrl ?? ''} onChange={(e) => setRule({ ...rule, webhookUrl: e.target.value || null })} />
            </label>
            <button className="btn primary">{saved ? 'Saved ✓' : 'Save'}</button>
          </form>
        )}
      </div>
    </>
  )
}
