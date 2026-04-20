import { useState, useRef, useEffect } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'
import api from '@/lib/api'
import {
  LayoutDashboard, Server, Ticket, Users, Bell, LogOut,
  Building2, Shield, Award, FileText, Settings, Menu, X, ChevronRight,
  Search, Download, Globe, History
} from 'lucide-react'
import { cn } from '@/lib/utils'

const BREADCRUMB_LABELS: Record<string, string> = {
  '': 'Dashboard',
  'clusters': 'Clusters',
  'tickets': 'Tickets',
  'admin': 'Admin',
  'users': 'Users',
  'notifications': 'Notifications',
  'audit': 'Activity Log',
  'operator': 'Operator',
  'tenants': 'Tenants',
  'licenses': 'Licenses',
  'recommendations': 'Recommendations',
  'rules': 'Audit Rules',
  'fleet': 'Fleet Overview',
  'bundles': 'Bundle',
}

export default function Layout() {
  const { role, tenantId, logout } = useAuthStore()
  const location = useLocation()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const isOperator = role === 'OPERATOR'
  const isAdmin = role === 'ADMIN' || isOperator

  const navItems = [
    { to: '/', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/clusters', label: 'Clusters', icon: Server },
    { to: '/tickets', label: 'Tickets', icon: Ticket },
  ]

  const adminItems = [
    { to: '/admin/users', label: 'Users', icon: Users },
    { to: '/admin/notifications', label: 'Notifications', icon: Bell },
    { to: '/admin/audit', label: 'Activity Log', icon: History },
  ]

  const operatorItems = [
    { to: '/operator', label: 'Operator Dashboard', icon: Shield },
    { to: '/operator/clusters', label: 'All Clusters', icon: Server },
    { to: '/operator/tenants', label: 'Tenants', icon: Building2 },
    { to: '/operator/licenses', label: 'Licenses', icon: Award },
    { to: '/operator/recommendations', label: 'Recommendations', icon: FileText },
    { to: '/operator/rules', label: 'Audit Rules', icon: Settings },
    { to: '/operator/fleet', label: 'Fleet Overview', icon: Globe },
  ]

  // Build breadcrumbs from path
  const pathSegments = location.pathname.split('/').filter(Boolean)
  const breadcrumbs = pathSegments.map((seg, i) => ({
    label: BREADCRUMB_LABELS[seg] || (seg.length > 12 ? seg.slice(0, 8) + '...' : seg),
    to: '/' + pathSegments.slice(0, i + 1).join('/'),
    isLast: i === pathSegments.length - 1,
  }))

  const sidebar = (
    <>
      <div className="p-5 border-b border-gray-100">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-primary flex items-center justify-center">
            <Shield className="w-4 h-4 text-white" />
          </div>
          <div>
            <h1 className="text-base font-semibold text-foreground tracking-tight">SupportPlane</h1>
            <p className="text-[11px] text-muted-foreground leading-none mt-0.5">
              {isOperator ? 'Operator Console' : tenantId}
            </p>
          </div>
        </div>
      </div>

      <nav className="flex-1 p-3 space-y-0.5 overflow-y-auto scrollbar-thin">
        {navItems.map((item) => (
          <NavLink key={item.to} {...item} active={location.pathname === item.to}
            onClick={() => setSidebarOpen(false)} />
        ))}

        {isAdmin && (
          <>
            <div className="pt-5 pb-1.5 px-3 text-[11px] font-semibold text-muted-foreground/70 uppercase tracking-wider">
              Administration
            </div>
            {adminItems.map((item) => (
              <NavLink key={item.to} {...item} active={location.pathname === item.to}
                onClick={() => setSidebarOpen(false)} />
            ))}
          </>
        )}

        {isOperator && (
          <>
            <div className="pt-5 pb-1.5 px-3 text-[11px] font-semibold text-muted-foreground/70 uppercase tracking-wider">
              Operations
            </div>
            {operatorItems.map((item) => (
              <NavLink key={item.to} {...item}
                active={location.pathname === item.to || (item.to !== '/operator' && location.pathname.startsWith(item.to))}
                onClick={() => setSidebarOpen(false)} />
            ))}
          </>
        )}
      </nav>

      <div className="p-3 border-t border-gray-100">
        <button
          onClick={logout}
          className="flex items-center gap-2.5 text-sm text-muted-foreground hover:text-foreground w-full px-3 py-2 rounded-md hover:bg-gray-50 transition-colors"
        >
          <LogOut className="w-4 h-4" />
          Sign out
        </button>
      </div>
    </>
  )

  return (
    <div className="flex h-screen bg-gray-50/80">
      {/* Desktop sidebar */}
      <aside className="hidden lg:flex w-60 bg-white border-r border-gray-200/80 shadow-sidebar flex-col shrink-0">
        {sidebar}
      </aside>

      {/* Mobile sidebar overlay */}
      {sidebarOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-black/20 backdrop-blur-sm" onClick={() => setSidebarOpen(false)} />
          <aside className="absolute left-0 top-0 bottom-0 w-60 bg-white shadow-lg flex flex-col animate-slide-in">
            {sidebar}
          </aside>
        </div>
      )}

      {/* Main content */}
      <main className="flex-1 overflow-y-auto scrollbar-thin">
        {/* Top bar */}
        <div className="sticky top-0 z-40 bg-white/95 backdrop-blur border-b border-gray-200/80 px-4 lg:px-8 py-2.5">
          <div className="flex items-center gap-3 max-w-7xl mx-auto">
            <button onClick={() => setSidebarOpen(true)} className="lg:hidden p-1.5 -ml-1.5 rounded-md hover:bg-gray-100"
              aria-label="Open menu">
              <Menu className="w-5 h-5 text-foreground" />
            </button>

            {/* Search */}
            <SearchBar />

            {/* Export dropdown */}
            <div className="hidden md:flex items-center gap-1 ml-auto">
              <a href="/api/v1/export/tickets" className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-gray-50 rounded-md transition-colors"
                download>
                <Download className="w-3.5 h-3.5" /> Tickets CSV
              </a>
              <a href="/api/v1/export/clusters" className="flex items-center gap-1.5 px-2.5 py-1.5 text-xs text-muted-foreground hover:text-foreground hover:bg-gray-50 rounded-md transition-colors"
                download>
                <Download className="w-3.5 h-3.5" /> Clusters CSV
              </a>
            </div>
          </div>
        </div>

        <div className="p-6 lg:p-8 max-w-7xl mx-auto">
          {/* Breadcrumbs */}
          {breadcrumbs.length > 0 && (
            <nav className="flex items-center gap-1 text-sm mb-5" aria-label="Breadcrumb">
              <Link to="/" className="text-muted-foreground hover:text-foreground transition-colors">
                Home
              </Link>
              {breadcrumbs.map((bc) => (
                <span key={bc.to} className="flex items-center gap-1">
                  <ChevronRight className="w-3.5 h-3.5 text-muted-foreground/50" />
                  {bc.isLast ? (
                    <span className="text-foreground font-medium">{bc.label}</span>
                  ) : (
                    <Link to={bc.to} className="text-muted-foreground hover:text-foreground transition-colors">
                      {bc.label}
                    </Link>
                  )}
                </span>
              ))}
            </nav>
          )}

          <div className="animate-in">
            <Outlet />
          </div>
        </div>
      </main>
    </div>
  )
}

function SearchBar() {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<any>(null)
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const handle = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handle)
    return () => document.removeEventListener('mousedown', handle)
  }, [])

  useEffect(() => {
    if (query.length < 2) { setResults(null); return }
    const timer = setTimeout(async () => {
      try {
        const { data } = await api.get('/search', { params: { q: query } })
        setResults(data)
        setOpen(true)
      } catch { /* ignore */ }
    }, 300)
    return () => clearTimeout(timer)
  }, [query])

  const go = (path: string) => { navigate(path); setOpen(false); setQuery('') }

  return (
    <div className="relative flex-1 max-w-md" ref={ref}>
      <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground/60" />
      <input
        type="text" value={query} onChange={(e) => setQuery(e.target.value)}
        onFocus={() => results && setOpen(true)}
        placeholder="Search tenants, clusters, tickets..."
        className="w-full pl-9 pr-3 py-1.5 border rounded-lg text-sm bg-gray-50/80 focus:bg-white focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary/40 transition-all"
      />
      {open && results && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-white border rounded-lg shadow-lg overflow-hidden z-50 max-h-80 overflow-y-auto">
          {results.tenants?.length > 0 && (
            <div>
              <div className="px-3 py-1.5 text-[11px] font-semibold text-muted-foreground/70 uppercase bg-gray-50">Tenants</div>
              {results.tenants.map((t: any) => (
                <button key={t.id} onClick={() => go(`/operator/tenants`)}
                  className="w-full text-left px-3 py-2 hover:bg-gray-50 flex items-center gap-2 text-sm">
                  <Building2 className="w-3.5 h-3.5 text-muted-foreground" /> {t.name} <span className="text-xs text-muted-foreground">({t.tenantId})</span>
                </button>
              ))}
            </div>
          )}
          {results.clusters?.length > 0 && (
            <div>
              <div className="px-3 py-1.5 text-[11px] font-semibold text-muted-foreground/70 uppercase bg-gray-50">Clusters</div>
              {results.clusters.map((c: any) => (
                <button key={c.id} onClick={() => go(`/clusters/${c.id}`)}
                  className="w-full text-left px-3 py-2 hover:bg-gray-50 flex items-center gap-2 text-sm">
                  <Server className="w-3.5 h-3.5 text-muted-foreground" /> {c.name}
                </button>
              ))}
            </div>
          )}
          {results.tickets?.length > 0 && (
            <div>
              <div className="px-3 py-1.5 text-[11px] font-semibold text-muted-foreground/70 uppercase bg-gray-50">Tickets</div>
              {results.tickets.map((t: any) => (
                <button key={t.id} onClick={() => go(`/tickets/${t.id}`)}
                  className="w-full text-left px-3 py-2 hover:bg-gray-50 flex items-center gap-2 text-sm">
                  <Ticket className="w-3.5 h-3.5 text-muted-foreground" /> {t.title}
                </button>
              ))}
            </div>
          )}
          {!results.tenants?.length && !results.clusters?.length && !results.tickets?.length && (
            <div className="px-3 py-4 text-sm text-muted-foreground text-center">No results for "{query}"</div>
          )}
        </div>
      )}
    </div>
  )
}

function NavLink({ to, label, icon: Icon, active, onClick }: {
  to: string; label: string; icon: any; active: boolean; onClick?: () => void
}) {
  return (
    <Link
      to={to}
      onClick={onClick}
      className={cn(
        "flex items-center gap-2.5 px-3 py-2 text-[13px] rounded-md transition-all duration-150",
        active
          ? "bg-primary/8 text-primary font-medium shadow-sm shadow-primary/5"
          : "text-muted-foreground hover:bg-gray-50 hover:text-foreground"
      )}
    >
      <Icon className={cn("w-4 h-4 shrink-0", active ? "text-primary" : "")} />
      <span className="truncate">{label}</span>
    </Link>
  )
}
