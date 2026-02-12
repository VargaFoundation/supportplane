import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { Server, Ticket, Building2, Users, Package, FileText, AlertTriangle } from 'lucide-react'

export default function OperatorDashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['operator-dashboard'],
    queryFn: () => api.get('/dashboard').then((r) => r.data),
  })

  const { data: recentTickets } = useQuery({
    queryKey: ['recent-tickets'],
    queryFn: () => api.get('/tickets', { params: { status: 'OPEN' } }).then((r) => r.data),
  })

  const { data: recentBundles } = useQuery({
    queryKey: ['recent-bundles'],
    queryFn: () => api.get('/bundles', { params: { limit: 10 } }).then((r) => r.data),
  })

  if (isLoading) return <div className="text-muted-foreground">Loading...</div>

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Operator Dashboard</h1>

      {/* Stats */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
        <StatCard icon={Building2} label="Tenants" value={data?.totalTenants || 0} />
        <StatCard icon={Server} label="Active Clusters" value={data?.activeClusters || 0}
          sub={`${data?.totalClusters || 0} total`} />
        <StatCard icon={Ticket} label="Open Tickets" value={data?.openTickets || 0} />
        <StatCard icon={Package} label="Total Bundles" value={data?.totalBundles || 0} />
        <StatCard icon={Users} label="Total Users" value={data?.totalUsers || 0} />
        <StatCard icon={FileText} label="Pending Recommendations" value={data?.pendingRecommendations || 0} />
        <StatCard icon={AlertTriangle} label="Critical Tickets" value={
          recentTickets?.filter((t: any) => t.priority === 'CRITICAL').length || 0
        } highlight />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Open Tickets */}
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">Recent Open Tickets</h2>
          <div className="space-y-3">
            {recentTickets?.slice(0, 8).map((t: any) => (
              <div key={t.id} className="flex items-center justify-between py-2 border-b last:border-0">
                <div>
                  <p className="text-sm font-medium">{t.title}</p>
                  <p className="text-xs text-muted-foreground">
                    {t.tenantName} — {t.createdByName}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 text-xs rounded-full ${
                    t.priority === 'CRITICAL' ? 'bg-red-100 text-red-800' :
                    t.priority === 'HIGH' ? 'bg-orange-100 text-orange-800' :
                    'bg-gray-100 text-gray-700'
                  }`}>{t.priority}</span>
                </div>
              </div>
            ))}
            {(!recentTickets || recentTickets.length === 0) && (
              <p className="text-sm text-muted-foreground">No open tickets.</p>
            )}
          </div>
        </div>

        {/* Recent Bundles */}
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-lg font-semibold mb-4">Recent Bundles</h2>
          <div className="space-y-3">
            {recentBundles?.slice(0, 8).map((b: any) => (
              <div key={b.id} className="flex items-center justify-between py-2 border-b last:border-0">
                <div>
                  <p className="text-sm font-medium">{b.filename}</p>
                  <p className="text-xs text-muted-foreground">
                    {b.clusterName || b.clusterId} — {new Date(b.receivedAt).toLocaleString()}
                  </p>
                </div>
                <span className="text-xs text-muted-foreground">
                  {(b.sizeBytes / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
            ))}
            {(!recentBundles || recentBundles.length === 0) && (
              <p className="text-sm text-muted-foreground">No bundles received yet.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

function StatCard({ icon: Icon, label, value, sub, highlight }: {
  icon: any; label: string; value: number; sub?: string; highlight?: boolean
}) {
  return (
    <div className={`rounded-lg border p-6 ${highlight && value > 0 ? 'bg-red-50 border-red-200' : 'bg-white'}`}>
      <div className="flex items-center gap-3 mb-3">
        <div className={`p-2 rounded-lg ${highlight && value > 0 ? 'bg-red-100' : 'bg-primary/10'}`}>
          <Icon className={`w-5 h-5 ${highlight && value > 0 ? 'text-red-600' : 'text-primary'}`} />
        </div>
        <span className="text-sm text-muted-foreground">{label}</span>
      </div>
      <div className="text-3xl font-bold">{value}</div>
      {sub && <p className="text-sm text-muted-foreground mt-1">{sub}</p>}
    </div>
  )
}
