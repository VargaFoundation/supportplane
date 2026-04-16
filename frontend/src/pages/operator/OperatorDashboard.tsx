import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Server, Ticket, Building2, Users, Package, FileText, AlertTriangle } from 'lucide-react'
import { StatCard, Card, CardSkeleton, PriorityBadge } from '@/components/ui'

export default function OperatorDashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['operator-dashboard'],
    queryFn: () => api.get('/dashboard').then((r) => r.data),
  })

  const { data: recentTickets } = useQuery({
    queryKey: ['recent-tickets'],
    queryFn: () => api.get('/tickets', { params: { status: 'OPEN' } }).then((r) => r.data),
  })

  if (isLoading) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight mb-6">Operator Dashboard</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {Array.from({ length: 7 }).map((_, i) => <CardSkeleton key={i} />)}
        </div>
      </div>
    )
  }

  const criticalCount = recentTickets?.filter((t: any) => t.priority === 'CRITICAL').length || 0

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight mb-6">Operator Dashboard</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5 mb-8">
        <StatCard icon={Building2} label="Tenants" value={data?.totalTenants || 0} />
        <StatCard icon={Server} label="Active Clusters" value={data?.activeClusters || 0}
          sub={`${data?.totalClusters || 0} total`} />
        <StatCard icon={Ticket} label="Open Tickets" value={data?.openTickets || 0} />
        <StatCard icon={Package} label="Total Bundles" value={data?.totalBundles || 0} />
        <StatCard icon={Users} label="Total Users" value={data?.totalUsers || 0} />
        <StatCard icon={FileText} label="Pending Recommendations" value={data?.pendingRecommendations || 0} />
        <Card className={`p-5 ${criticalCount > 0 ? 'bg-red-50/80 border-red-200' : ''}`}>
          <div className="flex items-center gap-3 mb-3">
            <div className={`p-2 rounded-lg ${criticalCount > 0 ? 'bg-red-100' : 'bg-primary/8'}`}>
              <AlertTriangle className={`w-4 h-4 ${criticalCount > 0 ? 'text-red-600' : 'text-primary'}`} />
            </div>
            <span className="text-[13px] text-muted-foreground">Critical Tickets</span>
          </div>
          <div className={`text-2xl font-semibold tracking-tight ${criticalCount > 0 ? 'text-red-700' : ''}`}>{criticalCount}</div>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Open Tickets */}
        <Card className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold">Recent Open Tickets</h2>
            <Link to="/tickets" className="text-xs text-primary hover:underline">View all</Link>
          </div>
          <div className="space-y-0 divide-y divide-gray-100">
            {recentTickets?.slice(0, 8).map((t: any) => (
              <Link key={t.id} to={`/tickets/${t.id}`}
                className="flex items-center justify-between py-2.5 hover:bg-gray-50/60 -mx-2 px-2 rounded transition-colors">
                <div className="min-w-0">
                  <p className="text-sm font-medium truncate">{t.title}</p>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {t.tenantName || 'Unknown'} {t.createdByName ? `— ${t.createdByName}` : ''}
                  </p>
                </div>
                <PriorityBadge value={t.priority} className="shrink-0 ml-3" />
              </Link>
            ))}
            {(!recentTickets || recentTickets.length === 0) && (
              <p className="text-sm text-muted-foreground py-4 text-center">No open tickets</p>
            )}
          </div>
        </Card>

        {/* Quick Links */}
        <Card className="p-6">
          <h2 className="text-base font-semibold mb-4">Quick Actions</h2>
          <div className="grid grid-cols-2 gap-3">
            {[
              { to: '/operator/clusters', icon: Server, label: 'All Clusters', desc: 'View and manage' },
              { to: '/operator/tenants', icon: Building2, label: 'Tenants', desc: 'Manage tenants' },
              { to: '/operator/licenses', icon: FileText, label: 'Licenses', desc: 'Create and manage' },
              { to: '/operator/recommendations', icon: AlertTriangle, label: 'Recommendations', desc: 'Review findings' },
            ].map((item) => (
              <Link key={item.to} to={item.to}
                className="flex items-center gap-3 p-3 rounded-lg border border-gray-100 hover:border-gray-200 hover:bg-gray-50/60 transition-all">
                <div className="p-2 bg-primary/8 rounded-lg">
                  <item.icon className="w-4 h-4 text-primary" />
                </div>
                <div>
                  <p className="text-sm font-medium">{item.label}</p>
                  <p className="text-xs text-muted-foreground">{item.desc}</p>
                </div>
              </Link>
            ))}
          </div>
        </Card>
      </div>
    </div>
  )
}
