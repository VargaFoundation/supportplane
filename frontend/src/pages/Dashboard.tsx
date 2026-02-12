import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth'
import api from '@/lib/api'
import { Server, Ticket, Users, Building2, FileText, Package } from 'lucide-react'

export default function Dashboard() {
  const role = useAuthStore((s) => s.role)
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api.get('/dashboard').then((r) => r.data),
  })

  if (isLoading) return <div className="text-muted-foreground">Loading...</div>

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard icon={Server} label="Clusters" value={data?.totalClusters || 0}
          sub={`${data?.activeClusters || 0} active`} />
        <StatCard icon={Ticket} label="Open Tickets" value={data?.openTickets || 0} />
        <StatCard icon={Users} label="Users" value={data?.totalUsers || 0} />
        {data?.totalBundles !== undefined && (
          <StatCard icon={Package} label="Bundles" value={data.totalBundles} />
        )}
        {role === 'OPERATOR' && (
          <>
            <StatCard icon={Building2} label="Tenants" value={data?.totalTenants || 0} />
            <StatCard icon={FileText} label="Pending Recommendations"
              value={data?.pendingRecommendations || 0} />
          </>
        )}
      </div>
    </div>
  )
}

function StatCard({ icon: Icon, label, value, sub }: {
  icon: any; label: string; value: number; sub?: string
}) {
  return (
    <div className="bg-white rounded-lg border p-6">
      <div className="flex items-center gap-3 mb-3">
        <div className="p-2 bg-primary/10 rounded-lg">
          <Icon className="w-5 h-5 text-primary" />
        </div>
        <span className="text-sm text-muted-foreground">{label}</span>
      </div>
      <div className="text-3xl font-bold">{value}</div>
      {sub && <p className="text-sm text-muted-foreground mt-1">{sub}</p>}
    </div>
  )
}
