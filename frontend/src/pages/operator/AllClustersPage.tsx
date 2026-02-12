import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Server, Search } from 'lucide-react'

export default function AllClustersPage() {
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')

  const { data: clusters, isLoading } = useQuery({
    queryKey: ['all-clusters'],
    queryFn: () => api.get('/clusters', { params: { all: true } }).then((r) => r.data),
  })

  const statusColor: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-800',
    PENDING: 'bg-yellow-100 text-yellow-800',
    DETACHED: 'bg-gray-100 text-gray-800',
  }

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
      <h1 className="text-2xl font-bold mb-6">All Clusters</h1>

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text" placeholder="Search by name, ID, or tenant..." value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border rounded-md text-sm"
          />
        </div>
        <div className="flex gap-1">
          {['ALL', 'ACTIVE', 'PENDING', 'DETACHED'].map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-1 text-xs rounded-full ${
                statusFilter === s ? 'bg-primary text-primary-foreground' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="text-muted-foreground">Loading clusters...</div>
      ) : (
        <div className="bg-white border rounded-lg overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Cluster</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Cluster ID</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Tenant</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Status</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Bundles</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Last Bundle</th>
              </tr>
            </thead>
            <tbody>
              {filtered?.map((c: any) => (
                <tr key={c.id} className="border-b last:border-0 hover:bg-gray-50">
                  <td className="p-3">
                    <Link to={`/clusters/${c.id}`} className="flex items-center gap-3">
                      <div className="p-1.5 bg-primary/10 rounded">
                        <Server className="w-4 h-4 text-primary" />
                      </div>
                      <span className="text-sm font-medium text-primary hover:underline">
                        {c.name || c.clusterId}
                      </span>
                    </Link>
                  </td>
                  <td className="p-3 text-sm font-mono text-muted-foreground">{c.clusterId}</td>
                  <td className="p-3 text-sm">{c.tenantName || '-'}</td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 text-xs rounded-full ${statusColor[c.status] || 'bg-gray-100'}`}>
                      {c.status}
                    </span>
                  </td>
                  <td className="p-3 text-sm text-muted-foreground">{c.bundleCount ?? '-'}</td>
                  <td className="p-3 text-xs text-muted-foreground">
                    {c.lastBundleAt ? new Date(c.lastBundleAt).toLocaleString() : 'Never'}
                  </td>
                </tr>
              ))}
              {(!filtered || filtered.length === 0) && (
                <tr>
                  <td colSpan={6} className="p-8 text-center text-muted-foreground text-sm">
                    No clusters found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
