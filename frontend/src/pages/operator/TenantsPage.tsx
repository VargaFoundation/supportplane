import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { Building2, Server, Users, Ticket } from 'lucide-react'

export default function TenantsPage() {
  const { data: tenants, isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.get('/tenants').then((r) => r.data),
  })

  if (isLoading) return <div className="text-muted-foreground">Loading tenants...</div>

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">Tenants</h1>

      <div className="grid gap-4">
        {tenants?.map((t: any) => (
          <div key={t.id} className="bg-white border rounded-lg p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-4">
                <div className="p-2 bg-primary/10 rounded-lg">
                  <Building2 className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h3 className="font-medium">{t.name}</h3>
                  <p className="text-sm text-muted-foreground font-mono">{t.tenantId}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span className={`px-2 py-1 text-xs rounded-full ${
                  t.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-600'
                }`}>
                  {t.active ? 'Active' : 'Inactive'}
                </span>
                {t.licenseTier && (
                  <span className="px-2 py-1 text-xs rounded-full bg-blue-100 text-blue-800">
                    {t.licenseTier}
                  </span>
                )}
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Server className="w-4 h-4" />
                <span>{t.clusterCount ?? 0} clusters</span>
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Users className="w-4 h-4" />
                <span>{t.userCount ?? 0} users</span>
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Ticket className="w-4 h-4" />
                <span>{t.openTicketCount ?? 0} open tickets</span>
              </div>
            </div>
          </div>
        ))}
        {(!tenants || tenants.length === 0) && (
          <div className="text-center py-12 text-muted-foreground">
            No tenants registered yet.
          </div>
        )}
      </div>
    </div>
  )
}
