import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Server, Search } from 'lucide-react'
import { Card, StatusBadge, TableSkeleton, EmptyState } from '@/components/ui'

export default function AllClustersPage() {
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const { data: clusters, isLoading } = useQuery({
    queryKey: ['all-clusters'],
    queryFn: () => api.get('/clusters', { params: { all: true } }).then((r) => r.data),
  })

  const filtered = clusters?.filter((c: any) => {
    const matchSearch = !search ||
      (c.name || '').toLowerCase().includes(search.toLowerCase()) ||
      (c.clusterId || '').toLowerCase().includes(search.toLowerCase()) ||
      (c.tenantName || '').toLowerCase().includes(search.toLowerCase())
    const matchStatus = statusFilter === 'ALL' || c.status === statusFilter
    return matchSearch && matchStatus
  })

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight mb-6">All Clusters</h1>

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text" placeholder="Search by name, ID, or tenant..." value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
          />
        </div>
        <div className="flex gap-1">
          {['ALL', 'ACTIVE', 'PENDING', 'DETACHED'].map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${
                statusFilter === s
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <TableSkeleton rows={6} cols={5} />
      ) : filtered?.length === 0 ? (
        <EmptyState icon={Server} title="No clusters found"
          description={search ? 'Try adjusting your search criteria.' : 'No clusters have been attached yet.'} />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50/80">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Cluster</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider hidden lg:table-cell">Cluster ID</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Tenant</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Status</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Last Bundle</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered?.map((c: any) => (
                <tr key={c.id} className="hover:bg-gray-50/60 transition-colors">
                  <td className="p-3">
                    <Link to={`/clusters/${c.id}`} className="flex items-center gap-3">
                      <div className="p-1.5 bg-primary/8 rounded-lg shrink-0">
                        <Server className="w-4 h-4 text-primary" />
                      </div>
                      <span className="text-sm font-medium text-primary hover:underline truncate">
                        {c.name || c.clusterId}
                      </span>
                    </Link>
                  </td>
                  <td className="p-3 hidden lg:table-cell">
                    <code className="text-xs font-mono text-muted-foreground bg-gray-50 px-1.5 py-0.5 rounded">
                      {c.clusterId?.slice(0, 16)}...
                    </code>
                  </td>
                  <td className="p-3 text-sm">{c.tenantName || '-'}</td>
                  <td className="p-3"><StatusBadge value={c.status} /></td>
                  <td className="p-3 text-xs text-muted-foreground">
                    {c.lastBundleAt ? new Date(c.lastBundleAt).toLocaleString() : <span className="italic">Never</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  )
}
