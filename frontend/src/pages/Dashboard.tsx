import { useQuery } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth'
import api from '@/lib/api'
import { Server, Ticket, Users, Building2, FileText, Package } from 'lucide-react'
import { StatCard, CardSkeleton } from '@/components/ui'

export default function Dashboard() {
  const role = useAuthStore((s) => s.role)
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard'],
    queryFn: () => api.get('/dashboard').then((r) => r.data),
  })

  if (isLoading) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight mb-6">Dashboard</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={i} />)}
        </div>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight mb-6">Dashboard</h1>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
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
