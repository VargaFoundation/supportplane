import { Outlet, Link, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/stores/auth'
import {
  LayoutDashboard, Server, Ticket, Users, Bell, LogOut,
  Building2, Shield, Award, FileText
} from 'lucide-react'
import { cn } from '@/lib/utils'

export default function Layout() {
  const { role, tenantId, logout } = useAuthStore()
  const location = useLocation()
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
  ]

  const operatorItems = [
    { to: '/operator', label: 'Operator Dashboard', icon: Shield },
    { to: '/operator/clusters', label: 'All Clusters', icon: Server },
    { to: '/operator/tenants', label: 'Tenants', icon: Building2 },
    { to: '/operator/licenses', label: 'Licenses', icon: Award },
    { to: '/operator/recommendations', label: 'Recommendations', icon: FileText },
  ]

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
        <div className="p-6 border-b border-gray-200">
          <h1 className="text-xl font-bold text-primary">SupportPlane</h1>
          <p className="text-xs text-muted-foreground mt-1">
            {isOperator ? 'Operator' : tenantId}
          </p>
        </div>

        <nav className="flex-1 p-4 space-y-1 overflow-y-auto">
          {navItems.map((item) => (
            <NavLink key={item.to} {...item} active={location.pathname === item.to} />
          ))}

          {isAdmin && (
            <>
              <div className="pt-4 pb-2 px-3 text-xs font-semibold text-muted-foreground uppercase">
                Admin
              </div>
              {adminItems.map((item) => (
                <NavLink key={item.to} {...item} active={location.pathname === item.to} />
              ))}
            </>
          )}

          {isOperator && (
            <>
              <div className="pt-4 pb-2 px-3 text-xs font-semibold text-muted-foreground uppercase">
                Operator
              </div>
              {operatorItems.map((item) => (
                <NavLink key={item.to} {...item} active={location.pathname === item.to} />
              ))}
            </>
          )}
        </nav>

        <div className="p-4 border-t border-gray-200">
          <button
            onClick={logout}
            className="flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground w-full px-3 py-2 rounded-md hover:bg-gray-100"
          >
            <LogOut className="w-4 h-4" />
            Logout
          </button>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-y-auto">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

function NavLink({ to, label, icon: Icon, active }: {
  to: string; label: string; icon: any; active: boolean
}) {
  return (
    <Link
      to={to}
      className={cn(
        "flex items-center gap-3 px-3 py-2 text-sm rounded-md transition-colors",
        active
          ? "bg-primary/10 text-primary font-medium"
          : "text-muted-foreground hover:bg-gray-100 hover:text-foreground"
      )}
    >
      <Icon className="w-4 h-4" />
      {label}
    </Link>
  )
}
