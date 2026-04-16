import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { FileText, Plus, CheckCircle, Send, CheckCircle2, AlertTriangle, XCircle, HelpCircle } from 'lucide-react'

export default function RecommendationsPage() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [severity, setSeverity] = useState('INFO')
  const [clusterId, setClusterId] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [sourceFilter, setSourceFilter] = useState('ALL')

  const { data: recommendations, isLoading } = useQuery({
    queryKey: ['all-recommendations', statusFilter],
    queryFn: () => api.get('/recommendations', {
      params: statusFilter !== 'ALL' ? { status: statusFilter } : {},
    }).then((r) => r.data),
  })

  const { data: clusters } = useQuery({
    queryKey: ['all-clusters'],
    queryFn: () => api.get('/clusters', { params: { all: true } }).then((r) => r.data),
  })

  const createMutation = useMutation({
    mutationFn: (data: { title: string; description: string; severity: string; clusterId: string }) =>
      api.post('/recommendations', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['all-recommendations'] })
      setShowCreate(false)
      setTitle('')
      setDescription('')
      setSeverity('INFO')
      setClusterId('')
    },
  })

  const validateMutation = useMutation({
    mutationFn: (id: number) => api.put(`/recommendations/${id}/validate`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['all-recommendations'] }),
  })

  const deliverMutation = useMutation({
    mutationFn: (id: number) => api.put(`/recommendations/${id}/deliver`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['all-recommendations'] }),
  })

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate({ title, description, severity, clusterId })
  }

  const severityColor: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800',
    WARNING: 'bg-yellow-100 text-yellow-800',
    INFO: 'bg-blue-100 text-blue-800',
  }

  const statusColor: Record<string, string> = {
    DRAFT: 'bg-gray-100 text-gray-800',
    VALIDATED: 'bg-blue-100 text-blue-800',
    DELIVERED: 'bg-green-100 text-green-800',
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Recommendations</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" /> Create Recommendation
        </button>
      </div>

      {showCreate && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">New Recommendation</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1">Cluster</label>
              <select
                value={clusterId} onChange={(e) => setClusterId(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" required
              >
                <option value="">Select cluster...</option>
                {clusters?.map((c: any) => (
                  <option key={c.id} value={c.id}>
                    {c.name || c.clusterId} ({c.tenantName})
                  </option>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Title</label>
                <input
                  type="text" value={title} onChange={(e) => setTitle(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" required
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Severity</label>
                <select
                  value={severity} onChange={(e) => setSeverity(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="INFO">Info</option>
                  <option value="WARNING">Warning</option>
                  <option value="CRITICAL">Critical</option>
                </select>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <textarea
                value={description} onChange={(e) => setDescription(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" rows={4} required
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                Create as Draft
              </button>
              <button type="button" onClick={() => setShowCreate(false)}
                className="px-4 py-2 border rounded-md text-sm">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Filters */}
      <div className="flex gap-4 mb-4">
        <div className="flex gap-1">
          {['ALL', 'DRAFT', 'VALIDATED', 'DELIVERED'].map((s) => (
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
        <div className="flex gap-1">
          {['ALL', 'OPERATOR', 'ENGINE'].map((s) => (
            <button
              key={s}
              onClick={() => setSourceFilter(s)}
              className={`px-3 py-1 text-xs rounded-full ${
                sourceFilter === s ? 'bg-indigo-600 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <div className="text-muted-foreground">Loading recommendations...</div>
      ) : (
        <div className="space-y-4">
          {recommendations?.filter((r: any) => sourceFilter === 'ALL' || r.source === sourceFilter).map((r: any) => (
            <div key={r.id} className="bg-white border rounded-lg p-5">
              <div className="flex items-start justify-between">
                <div className="flex items-start gap-4 flex-1">
                  <div className="p-2 bg-primary/10 rounded-lg mt-0.5">
                    {r.findingStatus === 'OK' ? <CheckCircle2 className="w-5 h-5 text-green-600" /> :
                     r.findingStatus === 'WARNING' ? <AlertTriangle className="w-5 h-5 text-yellow-600" /> :
                     r.findingStatus === 'CRITICAL' ? <XCircle className="w-5 h-5 text-red-600" /> :
                     r.findingStatus === 'UNKNOWN' ? <HelpCircle className="w-5 h-5 text-gray-500" /> :
                     <FileText className="w-5 h-5 text-primary" />}
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1 flex-wrap">
                      <h3 className="font-medium">{r.title}</h3>
                      <span className={`px-2 py-0.5 text-xs rounded-full ${severityColor[r.severity] || 'bg-gray-100'}`}>
                        {r.severity}
                      </span>
                      <span className={`px-2 py-0.5 text-xs rounded-full ${statusColor[r.status] || 'bg-gray-100'}`}>
                        {r.status}
                      </span>
                      {r.component && (
                        <span className="px-2 py-0.5 text-xs rounded-full bg-slate-100 text-slate-600">
                          {r.component}
                        </span>
                      )}
                      {r.category && (
                        <span className="px-2 py-0.5 text-xs rounded-full bg-purple-100 text-purple-700">
                          {r.category}
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-muted-foreground">{r.description}</p>
                    <div className="flex items-center gap-4 mt-2 text-xs text-muted-foreground">
                      <span>Cluster: {r.clusterName || r.clusterId}</span>
                      <span>Tenant: {r.tenantName || '-'}</span>
                      <span>Source: {r.source}</span>
                      {r.ruleCode && <span>Rule: {r.ruleCode}</span>}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2 ml-4">
                  {r.status === 'DRAFT' && (
                    <button
                      onClick={() => validateMutation.mutate(r.id)}
                      className="flex items-center gap-1 px-3 py-1.5 text-xs bg-blue-50 text-blue-700 rounded-md hover:bg-blue-100"
                    >
                      <CheckCircle className="w-3.5 h-3.5" /> Validate
                    </button>
                  )}
                  {r.status === 'VALIDATED' && (
                    <button
                      onClick={() => deliverMutation.mutate(r.id)}
                      className="flex items-center gap-1 px-3 py-1.5 text-xs bg-green-50 text-green-700 rounded-md hover:bg-green-100"
                    >
                      <Send className="w-3.5 h-3.5" /> Deliver
                    </button>
                  )}
                </div>
              </div>
            </div>
          ))}
          {(!recommendations || recommendations.length === 0) && (
            <div className="text-center py-12 text-muted-foreground">
              No recommendations found.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
