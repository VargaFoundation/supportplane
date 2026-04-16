import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { Ticket, Plus, Search } from 'lucide-react'
import { Button, Card, StatusBadge, PriorityBadge, TableSkeleton, EmptyState } from '@/components/ui'

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

  const filtered = tickets?.filter((t: any) =>
    !search || t.title.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Tickets</h1>
        <Button onClick={() => setShowCreate(true)}>
          <Plus className="w-4 h-4" /> New Ticket
        </Button>
      </div>

      {showCreate && (
        <Card className="p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Create Ticket</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1.5">Title</label>
              <input
                type="text" value={title} onChange={(e) => setTitle(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors" required
              />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1.5">Description</label>
              <textarea
                value={description} onChange={(e) => setDescription(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors" rows={4} required
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Priority</label>
                <select
                  value={priority} onChange={(e) => setPriority(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
                >
                  <option value="LOW">Low</option>
                  <option value="MEDIUM">Medium</option>
                  <option value="HIGH">High</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Cluster (optional)</label>
                <select
                  value={clusterId} onChange={(e) => setClusterId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
                >
                  <option value="">None</option>
                  {clusters?.map((c: any) => (
                    <option key={c.id} value={c.id}>{c.name || c.clusterId}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex gap-2">
              <Button type="submit" loading={createMutation.isPending}>Create</Button>
              <Button variant="secondary" type="button" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      {/* Filters */}
      <div className="flex items-center gap-4 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
          <input
            type="text" placeholder="Search tickets..." value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
          />
        </div>
        <div className="flex gap-1">
          {['ALL', 'OPEN', 'IN_PROGRESS', 'WAITING', 'RESOLVED', 'CLOSED'].map((s) => (
            <button
              key={s}
              onClick={() => setFilter(s)}
              className={`px-3 py-1.5 text-xs font-medium rounded-full transition-colors ${
                filter === s
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {s.replace('_', ' ')}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <TableSkeleton rows={5} cols={5} />
      ) : filtered?.length === 0 ? (
        <EmptyState icon={Ticket} title="No tickets found"
          description={filter !== 'ALL' ? `No tickets with status "${filter.replace('_', ' ')}".` : 'Create your first ticket to get started.'}
        />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50/80">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Title</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Status</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Priority</th>
                {role === 'OPERATOR' && (
                  <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Tenant</th>
                )}
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Assigned</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Updated</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {filtered?.map((t: any) => (
                <tr key={t.id} className="hover:bg-gray-50/60 transition-colors">
                  <td className="p-3">
                    <Link to={`/tickets/${t.id}`} className="text-sm font-medium text-primary hover:underline">
                      <div className="flex items-center gap-2">
                        <Ticket className="w-4 h-4 shrink-0" />
                        <span className="truncate max-w-[300px]">{t.title}</span>
                      </div>
                    </Link>
                  </td>
                  <td className="p-3"><StatusBadge value={t.status} /></td>
                  <td className="p-3"><PriorityBadge value={t.priority} /></td>
                  {role === 'OPERATOR' && (
                    <td className="p-3 text-sm text-muted-foreground">{t.tenantName || '-'}</td>
                  )}
                  <td className="p-3 text-sm text-muted-foreground">{t.assignedToName || <span className="italic">Unassigned</span>}</td>
                  <td className="p-3 text-xs text-muted-foreground">
                    {t.updatedAt ? new Date(t.updatedAt).toLocaleDateString() : '-'}
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
