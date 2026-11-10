import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { api, auth } from '../api/client'
import { live } from '../api/ws'
import type { Alert, UserInfo } from '../api/types'

export default function Layout() {
  const nav = useNavigate()
  const [user, setUser] = useState<UserInfo | null>(null)
  const [unread, setUnread] = useState(0)
  const [toast, setToast] = useState<Alert | null>(null)

  useEffect(() => {
    api.me().then(setUser).catch(() => nav('/login'))
    api.unreadCount().then((r) => setUnread(r.unread)).catch(() => {})
  }, [nav])

  // Live per-user alerts: bump the badge and show a toast
  useEffect(() => {
    if (!user) return
    return live.subscribe<Alert>(`/topic/users/${user.id}/alerts`, (a) => {
      setUnread((n) => n + 1)
      setToast(a)
      setTimeout(() => setToast((t) => (t?.id === a.id ? null : t)), 6000)
    })
  }, [user])

  const logout = () => {
    auth.clear()
    live.disconnect()
    nav('/login')
  }

  return (
    <div className="app">
      <header className="topbar">
        <Link to="/projects" className="brand">
          <span className="brand-mark">◉</span> DepWatch
        </Link>
        <nav>
          <NavLink to="/projects">Projects</NavLink>
          <NavLink to="/alerts">
            Alerts {unread > 0 && <span className="badge">{unread}</span>}
          </NavLink>
        </nav>
        <div className="spacer" />
        {user && (
          <div className="user">
            <span>{user.displayName}</span>
            <button className="btn-link" onClick={logout}>Sign out</button>
          </div>
        )}
      </header>
      <main className="content">
        <Outlet context={{ setUnread }} />
      </main>
      {toast && (
        <div className={`toast sev-${toast.severity.toLowerCase()}`} onClick={() => nav(`/scans/${toast.scanId}`)}>
          <strong>{toast.title}</strong>
          <div className="muted small">{toast.projectName}</div>
        </div>
      )}
    </div>
  )
}
