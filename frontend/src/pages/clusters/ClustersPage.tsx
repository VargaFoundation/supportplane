import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Plus, Server, Trash2, Unplug, Copy, Check } from 'lucide-react'
import { Button, Card, StatusBadge, PageLoader, EmptyState } from '@/components/ui'

export default function ClustersPage() {
  const [showAttach, setShowAttach] = useState(false)
  const [clusterId, setClusterId] = useState('')
  const [clusterName, setClusterName] = useState('')
  const [otpResult, setOtpResult] = useState<{ otpCode: string } | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const { data: clusters, isLoading } = useQuery({
    queryKey: ['clusters'],
    queryFn: () => api.get('/clusters').then((r) => r.data),
  })

  const attachMutation = useMutation({
    mutationFn: (data: { clusterId: string; name: string }) =>
      api.post('/clusters/attach', data).then((r) => r.data),
    onSuccess: (data) => {
      setOtpResult(data)
      queryClient.invalidateQueries({ queryKey: ['clusters'] })
    },
  })

  const detachMutation = useMutation({
    mutationFn: (id: number) => api.post(`/clusters/${id}/detach`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clusters'] }),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/clusters/${id}`),
    onSuccess: () => {
      setConfirmDelete(null)
      queryClient.invalidateQueries({ queryKey: ['clusters'] })
    },
  })

  const handleAttach = (e: React.FormEvent) => {
    e.preventDefault()
    attachMutation.mutate({ clusterId, name: clusterName })
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(text)
    setTimeout(() => setCopiedId(null), 2000)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Clusters</h1>
        <Button onClick={() => { setShowAttach(true); setOtpResult(null) }}>
          <Plus className="w-4 h-4" /> Attach Cluster
        </Button>
      </div>

      {showAttach && (
        <Card className="p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Attach a Cluster</h2>
          {!otpResult ? (
            <form onSubmit={handleAttach} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Cluster ID <span className="text-muted-foreground font-normal">(from ODPSC mpack)</span></label>
                <input type="text" value={clusterId} onChange={(e) => setClusterId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors" required />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Display Name</label>
                <input type="text" value={clusterName} onChange={(e) => setClusterName(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
                  placeholder="Optional friendly name" />
              </div>
              <div className="flex gap-2">
                <Button type="submit" loading={attachMutation.isPending}>Generate OTP</Button>
                <Button variant="secondary" type="button" onClick={() => setShowAttach(false)}>Cancel</Button>
              </div>
            </form>
          ) : (
            <div className="space-y-3">
              <div className="bg-emerald-50 border border-emerald-200 p-5 rounded-lg">
                <p className="text-sm font-medium text-emerald-800">OTP Generated Successfully</p>
                <p className="text-3xl font-mono font-bold text-emerald-900 mt-2 tracking-wider">{otpResult.otpCode}</p>
                <p className="text-sm text-emerald-700 mt-3">
                  Enter this OTP in Ambari: <strong>ODPSC Service &gt; Configs &gt; attachment_otp</strong>
                </p>
                <p className="text-xs text-emerald-600 mt-1">This OTP expires in 10 minutes.</p>
              </div>
              <Button variant="secondary" onClick={() => { setShowAttach(false); setOtpResult(null) }}>Close</Button>
            </div>
          )}
        </Card>
      )}

      {isLoading ? (
        <PageLoader message="Loading clusters..." />
      ) : clusters?.length === 0 ? (
        <EmptyState icon={Server} title="No clusters attached"
          description="Attach your first cluster by clicking the button above."
          action={<Button onClick={() => { setShowAttach(true); setOtpResult(null) }}><Plus className="w-4 h-4" /> Attach Cluster</Button>}
        />
      ) : (
        <div className="grid gap-3">
          {clusters?.map((cluster: any) => (
            <Card key={cluster.id} hover className="p-4">
              <div className="flex items-center justify-between">
                <Link to={`/clusters/${cluster.id}`} className="flex items-center gap-4 flex-1 min-w-0">
                  <div className="p-2 bg-primary/8 rounded-lg shrink-0">
                    <Server className="w-5 h-5 text-primary" />
                  </div>
                  <div className="min-w-0">
                    <h3 className="font-medium text-sm">{cluster.name || cluster.clusterId}</h3>
                    <div className="flex items-center gap-2 mt-1">
                      <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded font-mono text-gray-600 truncate max-w-[200px]">
                        {cluster.clusterId}
                      </code>
                      <button
                        onClick={(e) => { e.preventDefault(); copyToClipboard(cluster.clusterId) }}
                        className="p-0.5 text-gray-400 hover:text-gray-600 transition-colors"
                        title="Copy Cluster ID" aria-label="Copy Cluster ID"
                      >
                        {copiedId === cluster.clusterId ? (
                          <Check className="w-3.5 h-3.5 text-emerald-600" />
                        ) : (
                          <Copy className="w-3.5 h-3.5" />
                        )}
                      </button>
                    </div>
                  </div>
                  <StatusBadge value={cluster.status} />
                </Link>
                <div className="flex items-center gap-1 ml-4 shrink-0">
                  {cluster.status !== 'DETACHED' && (
                    <button
                      onClick={() => detachMutation.mutate(cluster.id)}
                      className="p-2 text-muted-foreground hover:text-amber-600 transition-colors rounded-md hover:bg-amber-50"
                      title="Detach cluster" aria-label="Detach cluster"
                    >
                      <Unplug className="w-4 h-4" />
                    </button>
                  )}
                  {confirmDelete === cluster.id ? (
                    <div className="flex items-center gap-1">
                      <Button size="sm" variant="destructive" onClick={() => deleteMutation.mutate(cluster.id)}>Confirm</Button>
                      <Button size="sm" variant="secondary" onClick={() => setConfirmDelete(null)}>Cancel</Button>
                    </div>
                  ) : (
                    <button
                      onClick={() => setConfirmDelete(cluster.id)}
                      className="p-2 text-muted-foreground hover:text-destructive transition-colors rounded-md hover:bg-red-50"
                      title="Delete cluster permanently" aria-label="Delete cluster"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  )}
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
