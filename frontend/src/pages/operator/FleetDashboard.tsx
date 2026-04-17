import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { Globe, Server, Building2, Package, Cpu, MapPin, Layers, HardDrive } from 'lucide-react'
import { Card, StatCard, CardSkeleton, StatusBadge } from '@/components/ui'

export default function FleetDashboard() {
  const { data, isLoading } = useQuery({
    queryKey: ['fleet-stats'],
    queryFn: () => api.get('/fleet/stats').then((r) => r.data),
  })

  if (isLoading) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight mb-6">Fleet Overview</h1>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
          {Array.from({ length: 8 }).map((_, i) => <CardSkeleton key={i} />)}
        </div>
      </div>
    )
  }

  const byStatus = data?.byStatus || {}
  const byVersion = data?.byVersion || {}
  const byOs = data?.byOs || {}
  const byGeo = data?.byGeo || {}
  const clusters = data?.clusters || []

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Fleet Overview</h1>
        <p className="text-sm text-muted-foreground mt-1">Global view of all deployed clusters and installations</p>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-5 gap-5 mb-8">
        <StatCard icon={Server} label="Total Clusters" value={data?.totalClusters || 0} />
        <StatCard icon={HardDrive} label="Total Servers" value={data?.totalServers || 0} />
        <StatCard icon={Building2} label="Tenants" value={data?.totalTenants || 0} />
        <StatCard icon={Package} label="Bundles Received" value={data?.totalBundles || 0} />
        <StatCard icon={Layers} label="Avg Cluster Size" value={`${data?.avgClusterSize || 0} nodes`} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-8">
        {/* Status distribution */}
        <Card className="p-6">
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <Cpu className="w-4 h-4 text-primary" /> Cluster Status
          </h2>
          <div className="space-y-2">
            {Object.entries(byStatus).map(([status, count]) => (
              <div key={status} className="flex items-center justify-between">
                <StatusBadge value={status} />
                <div className="flex items-center gap-3">
                  <div className="w-32 h-2 bg-gray-100 rounded-full overflow-hidden">
                    <div className="h-full bg-primary/60 rounded-full transition-all"
                      style={{ width: `${((count as number) / (data?.totalClusters || 1)) * 100}%` }} />
                  </div>
                  <span className="text-sm font-medium w-8 text-right">{count as number}</span>
                </div>
              </div>
            ))}
          </div>
        </Card>

        {/* Stack versions */}
        <Card className="p-6">
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <Layers className="w-4 h-4 text-primary" /> Stack Versions
          </h2>
          {Object.keys(byVersion).length === 0 ? (
            <p className="text-sm text-muted-foreground">No version data yet (awaiting first bundle with topology)</p>
          ) : (
            <div className="space-y-2">
              {Object.entries(byVersion).map(([version, count]) => (
                <div key={version} className="flex items-center justify-between">
                  <code className="text-sm bg-gray-100 px-2 py-0.5 rounded font-mono">{version}</code>
                  <div className="flex items-center gap-3">
                    <div className="w-32 h-2 bg-gray-100 rounded-full overflow-hidden">
                      <div className="h-full bg-blue-400 rounded-full"
                        style={{ width: `${((count as number) / (data?.totalClusters || 1)) * 100}%` }} />
                    </div>
                    <span className="text-sm font-medium w-8 text-right">{count as number}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* OS distribution */}
        <Card className="p-6">
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <HardDrive className="w-4 h-4 text-primary" /> Operating Systems
          </h2>
          {Object.keys(byOs).length === 0 ? (
            <p className="text-sm text-muted-foreground">No OS data yet</p>
          ) : (
            <div className="space-y-2">
              {Object.entries(byOs).map(([os, count]) => (
                <div key={os} className="flex items-center justify-between">
                  <span className="text-sm truncate max-w-[200px]" title={os}>{os}</span>
                  <span className="text-sm font-medium">{count as number} cluster{(count as number) > 1 ? 's' : ''}</span>
                </div>
              ))}
            </div>
          )}
        </Card>

        {/* Geo distribution */}
        <Card className="p-6">
          <h2 className="text-base font-semibold mb-4 flex items-center gap-2">
            <Globe className="w-4 h-4 text-primary" /> Geographic Distribution
          </h2>
          {Object.keys(byGeo).length === 0 ? (
            <p className="text-sm text-muted-foreground">No geolocation data yet</p>
          ) : (
            <div className="space-y-2">
              {Object.entries(byGeo).map(([country, count]) => (
                <div key={country} className="flex items-center justify-between">
                  <span className="text-sm">{country}</span>
                  <span className="text-sm font-medium">{count as number}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Cluster list with locations */}
      <Card className="overflow-hidden">
        <div className="p-4 border-b bg-gray-50/80">
          <h2 className="text-base font-semibold flex items-center gap-2">
            <MapPin className="w-4 h-4 text-primary" /> All Installations ({clusters.length})
          </h2>
        </div>
        <table className="w-full">
          <thead>
            <tr className="border-b bg-gray-50/50">
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Cluster</th>
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Tenant</th>
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Status</th>
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Version</th>
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Nodes</th>
              <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">IP / Location</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {clusters.map((c: any) => (
              <tr key={c.id} className="hover:bg-gray-50/60 transition-colors">
                <td className="p-3">
                  <div>
                    <span className="text-sm font-medium">{c.name}</span>
                    <div className="text-xs text-muted-foreground font-mono truncate max-w-[200px]">{c.clusterId}</div>
                  </div>
                </td>
                <td className="p-3 text-sm">{c.tenantName || <span className="text-muted-foreground italic">Orphan</span>}</td>
                <td className="p-3"><StatusBadge value={c.status} /></td>
                <td className="p-3">
                  {c.stackVersion ? (
                    <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono">{c.stackVersion}</code>
                  ) : <span className="text-xs text-muted-foreground">-</span>}
                </td>
                <td className="p-3 text-sm">{c.hostCount || '-'}</td>
                <td className="p-3">
                  <div className="text-sm">{c.sourceIp || '-'}</div>
                  {c.geoLocation && <div className="text-xs text-muted-foreground">{c.geoLocation}</div>}
                </td>
              </tr>
            ))}
            {clusters.length === 0 && (
              <tr>
                <td colSpan={6} className="p-8 text-center text-sm text-muted-foreground">
                  No clusters discovered yet. Clusters appear automatically when they send their first bundle.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </Card>
    </div>
  )
}
