import { useEffect, useState } from 'react'
import { live } from '../api/ws'
import type { ScanProgress } from '../api/types'

const STAGES: ScanProgress['stage'][] = ['PARSING', 'QUERYING', 'ENRICHING', 'DIFFING', 'COMPLETED']
const LABELS: Record<string, string> = {
  QUEUED: 'Queued',
  PARSING: 'Parsing manifest',
  QUERYING: 'Querying OSV',
  ENRICHING: 'Fetching details',
  DIFFING: 'Comparing with last scan',
  COMPLETED: 'Done',
  FAILED: 'Failed',
}

/**
  * Subscribes to /topic/scans/{scanId} and shows the live stage and progress.
  * onDone fires on COMPLETED / FAILED so the parent can refresh its data.
 */
export default function ScanProgressBar({ scanId, onDone }: { scanId: string; onDone?: (p: ScanProgress) => void }) {
  const [progress, setProgress] = useState<ScanProgress | null>(null)

  useEffect(() => {
    return live.subscribe<ScanProgress>(`/topic/scans/${scanId}`, (p) => {
      setProgress(p)
      if (p.stage === 'COMPLETED' || p.stage === 'FAILED') onDone?.(p)
    })
  }, [scanId, onDone])

  const stage = progress?.stage ?? 'QUEUED'
  const idx = STAGES.indexOf(stage)
  const pct = progress && progress.total > 0 ? Math.round((progress.current / progress.total) * 100) : null

  return (
    <div className={`progress ${stage === 'FAILED' ? 'progress-failed' : ''}`}>
      <div className="progress-stages">
        {STAGES.map((s, i) => (
          <span key={s} className={`stage ${i < idx ? 'done' : ''} ${i === idx ? 'active' : ''}`}>
            {LABELS[s]}
          </span>
        ))}
      </div>
      <div className="progress-line">
        <span className="pulse" />
        {LABELS[stage]}
        {pct != null && stage !== 'COMPLETED' && ` · ${progress!.current}/${progress!.total} (${pct}%)`}
        {progress?.message && <span className="muted"> — {progress.message}</span>}
      </div>
    </div>
  )
}
