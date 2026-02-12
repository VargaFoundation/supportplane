import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { Ticket, Plus, Search } from 'lucide-react'

export default function TicketsPage() {
  const role = useAuthStore((s) => s.role)
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [filter, setFilter] = useState<string>('ALL')
  const [search, setSearch] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [priority, setPriority] = useState('MEDIUM')
  const [clusterId, setClusterId] = useState('')

  const { data: tickets, isLoading } = useQuery({
    queryKey: ['tickets', filter],
    queryFn: () => api.get('/tickets', { params: filter !== 'ALL' ? { status: filter } : {} }).then((r) => r.data),
  })

  const { data: clusters } = useQuery({
    queryKey: ['clusters'],
    queryFn: () => api.get('/clusters').then((r) => r.data),
  })

  const createMutation = useMutation({
    mutationFn: (data: { title: string; description: string; priority: string; clusterId?: string }) =>
      api.post('/tickets', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tickets'] })
      setShowCreate(false)
      setTitle('')
      setDescription('')
      setPriority('MEDIUM')
      setClusterId('')
    },
  })

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate({
      title,
      description,
      priority,
      clusterId: clusterId || undefined,
    })
  }

  const priorityColor: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800',
    HIGH: 'bg-orange-100 text-orange-800',
    MEDIUM: 'bg-yellow-100 text-yellow-800',
    LOW: 'bg-blue-100 text-blue-800',
  }

  const statusColor: Record<string, string> = {
    OPEN: 'bg-green-100 text-green-800',
    IN_PROGRESS: 'bg-blue-100 text-blue-800',
    WAITING: 'bg-yellow-100 text-yellow-800',
    RESOLVED: 'bg-gray-100 text-gray-800',
    CLOSED: 'bg-gray-100 text-gray-600',
  }

  const filtered = tickets?.filter((t: any) =>
    !search || t.title.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Tickets</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" /> New Ticket
        </button>
      </div>

      {showCreate && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Create Ticket</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Title</label>
              <input
                type="text" value={title} onChange={(e) => setTitle(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <textarea
                value={description} onChange={(e) => setDescription(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" rows={4} required
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Priority</label>
                <select
                  value={priority} onChange={(e) => setPriority(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Cluster (optional)</label>
                <select
                  value={clusterId} onChange={(e) => setClusterId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="">None</option>
                  {clusters?.map((c: any) => (
                    <option key={c.id} value={c.id}>{c.name || c.clusterId}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex gap-2">
              <button type="submit" className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                Create
              </button>
              <button type="button" onClick={() => setShowCreate(false)}
                className="px-4 py-2 border rounded-md text-sm">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text" placeholder="Search tickets..." value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border rounded-md text-sm"
          />
        </div>
        <div className="flex gap-1">
          {['ALL', 'OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'].map((s) => (
            <button
              key={s}
              onClick={() => setFilter(s)}
              className={`px-3 py-1 text-xs rounded-full ${
                filter === s ? 'bg-primary text-primary-foreground' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {s.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="text-muted-foreground">Loading tickets...</div>
      ) : (
        <div className="bg-white border rounded-lg overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Title</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Status</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Priority</th>
                {role === 'OPERATOR' && (
                  <th className="text-left p-3 text-xs font-medium text-muted-foreground">Tenant</th>
                )}
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Assigned</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Updated</th>
              </tr>
            </thead>
            <tbody>
              {filtered?.map((t: any) => (
                <tr key={t.id} className="border-b last:border-0 hover:bg-gray-50">
                  <td className="p-3">
                    <Link to={`/tickets/${t.id}`} className="text-sm font-medium text-primary hover:underline">
                      <div className="flex items-center gap-2">
                        <Ticket className="w-4 h-4" />
                        {t.title}
                      </div>
                    </Link>
                  </td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 text-xs rounded-full ${statusColor[t.status] || 'bg-gray-100'}`}>
                      {t.status?.replace('_', ' ')}
                    </span>
                  </td>
                  <td className="p-3">
                    <span className={`px-2 py-0.5 text-xs rounded-full ${priorityColor[t.priority] || 'bg-gray-100'}`}>
                      {t.priority}
                    </span>
                  </td>
                  {role === 'OPERATOR' && (
                    <td className="p-3 text-sm text-muted-foreground">{t.tenantName || '-'}</td>
                  )}
                  <td className="p-3 text-sm text-muted-foreground">{t.assignedToName || 'Unassigned'}</td>
                  <td className="p-3 text-xs text-muted-foreground">
                    {t.updatedAt ? new Date(t.updatedAt).toLocaleDateString() : '-'}
                  </td>
                </tr>
              ))}
              {(!filtered || filtered.length === 0) && (
                <tr>
                  <td colSpan={role === 'OPERATOR' ? 6 : 5} className="p-8 text-center text-muted-foreground text-sm">
                    No tickets found.
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
