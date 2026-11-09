import type { Severity } from '../api/types'

export default function SeverityBadge({ severity, score }: { severity: Severity; score?: number | null }) {
  return (
    <span className={`sev sev-${severity.toLowerCase()}`} title={score != null ? `CVSS ${score}` : undefined}>
      {severity}
      {score != null && <span className="sev-score">{score.toFixed(1)}</span>}
    </span>
  )
}

export function SeverityBar({ critical, high, medium, low }: { critical: number; high: number; medium: number; low: number }) {
  const total = critical + high + medium + low
  if (total === 0) return <span className="muted">No vulnerabilities</span>
  const seg = (n: number, cls: string) =>
    n > 0 ? <span className={`bar-seg ${cls}`} style={{ flex: n }} title={`${n}`} /> : null
  return (
    <div className="sevbar-wrap">
      <div className="sevbar">
        {seg(critical, 'sev-critical')}
        {seg(high, 'sev-high')}
        {seg(medium, 'sev-medium')}
        {seg(low, 'sev-low')}
      </div>
      <span className="small muted">
        {critical > 0 && <b className="c-critical">{critical}C </b>}
        {high > 0 && <b className="c-high">{high}H </b>}
        {medium > 0 && <b className="c-medium">{medium}M </b>}
        {low > 0 && <b className="c-low">{low}L</b>}
      </span>
    </div>
  )
}
