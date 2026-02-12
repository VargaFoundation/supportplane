import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import api from '@/lib/api'
import { Plus, Server, Trash2 } from 'lucide-react'

export default function ClustersPage() {
  const [showAttach, setShowAttach] = useState(false)
  const [clusterId, setClusterId] = useState('')
  const [clusterName, setClusterName] = useState('')
  const [otpResult, setOtpResult] = useState<{ otpCode: string } | null>(null)
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
    mutationFn: (id: number) => api.delete(`/clusters/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['clusters'] }),
  })

  const handleAttach = (e: React.FormEvent) => {
    e.preventDefault()
    attachMutation.mutate({ clusterId, name: clusterName })
  }

  const statusColor: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-800',
    PENDING: 'bg-yellow-100 text-yellow-800',
    DETACHED: 'bg-gray-100 text-gray-800',
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Clusters</h1>
        <button onClick={() => { setShowAttach(true); setOtpResult(null) }}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90">
          <Plus className="w-4 h-4" /> Attach Cluster
        </button>
      </div>

      {showAttach && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Attach a Cluster</h2>
          {!otpResult ? (
            <form onSubmit={handleAttach} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-1">Cluster ID (from ODPSC mpack)</label>
                <input type="text" value={clusterId} onChange={(e) => setClusterId(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" required />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Display Name</label>
                <input type="text" value={clusterName} onChange={(e) => setClusterName(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" />
              </div>
              <div className="flex gap-2">
                <button type="submit" className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                  Generate OTP
                </button>
                <button type="button" onClick={() => setShowAttach(false)}
                  className="px-4 py-2 border rounded-md text-sm">Cancel</button>
              </div>
            </form>
          ) : (
            <div className="space-y-3">
              <div className="bg-green-50 border border-green-200 p-4 rounded-md">
                <p className="text-sm font-medium text-green-800">OTP Generated Successfully</p>
                <p className="text-3xl font-mono font-bold text-green-900 mt-2">{otpResult.otpCode}</p>
                <p className="text-sm text-green-700 mt-2">
                  Enter this OTP in Ambari: ODPSC Service &gt; Configs &gt; attachment_otp
                </p>
                <p className="text-xs text-green-600 mt-1">This OTP expires in 10 minutes.</p>
              </div>
              <button onClick={() => { setShowAttach(false); setOtpResult(null) }}
                className="px-4 py-2 border rounded-md text-sm">Close</button>
            </div>
          )}
        </div>
      )}

      {isLoading ? (
        <div className="text-muted-foreground">Loading clusters...</div>
      ) : (
        <div className="grid gap-4">
          {clusters?.map((cluster: any) => (
            <div key={cluster.id} className="bg-white border rounded-lg p-5 flex items-center justify-between">
              <Link to={`/clusters/${cluster.id}`} className="flex items-center gap-4 flex-1">
                <div className="p-2 bg-primary/10 rounded-lg">
                  <Server className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h3 className="font-medium">{cluster.name || cluster.clusterId}</h3>
                  <p className="text-sm text-muted-foreground font-mono">{cluster.clusterId}</p>
                </div>
                <span className={`px-2 py-1 text-xs rounded-full ${statusColor[cluster.status] || 'bg-gray-100'}`}>
                  {cluster.status}
                </span>
              </Link>
              <button onClick={() => detachMutation.mutate(cluster.id)}
                className="p-2 text-muted-foreground hover:text-destructive">
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          ))}
          {clusters?.length === 0 && (
            <div className="text-center py-12 text-muted-foreground">
              No clusters attached yet. Click "Attach Cluster" to get started.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
