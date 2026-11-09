import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError, auth } from '../api/client'

export default function LoginPage() {
  const nav = useNavigate()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const res = mode === 'login' ? await api.login(email, password) : await api.register(email, password, name)
      auth.set(res.accessToken)
      nav('/projects')
    } catch (err) {
      const msg = err instanceof ApiError
        ? (err.fieldErrors ? Object.values(err.fieldErrors).join('; ') : err.message)
        : 'Network error'
      setError(msg)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <form className="card auth-card" onSubmit={submit}>
        <h1><span className="brand-mark">◉</span> DepWatch</h1>
        <p className="muted">Track vulnerabilities in your open-source dependencies.</p>
        {mode === 'register' && (
          <label>Display name<input value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} /></label>
        )}
        <label>Email<input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></label>
        <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={8} /></label>
        {error && <div className="error">{error}</div>}
        <button className="btn primary" disabled={busy}>{busy ? '…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        <button type="button" className="btn-link" onClick={() => setMode(mode === 'login' ? 'register' : 'login')}>
          {mode === 'login' ? 'No account? Register' : 'Have an account? Sign in'}
        </button>
      </form>
    </div>
  )
}
