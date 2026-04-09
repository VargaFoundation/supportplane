import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts'
import { Server, Package, FileText, Copy, Check } from 'lucide-react'
import { useState } from 'react'

export default function ClusterDetailPage() {
  const { id } = useParams()

  const { data: cluster } = useQuery({
    queryKey: ['cluster', id],
    queryFn: () => api.get(`/clusters/${id}`).then((r) => r.data),
  })

  const { data: bundles } = useQuery({
    queryKey: ['cluster-bundles', id],
    queryFn: () => api.get(`/clusters/${id}/bundles`).then((r) => r.data),
  })

  const { data: recommendations } = useQuery({
    queryKey: ['cluster-recommendations', id],
    queryFn: () => api.get(`/clusters/${id}/recommendations`).then((r) => r.data),
  })

  const [copied, setCopied] = useState(false)
  const copyClusterId = () => {
    if (cluster) {
      navigator.clipboard.writeText(cluster.clusterId)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    }
  }

  if (!cluster) return <div className="text-muted-foreground">Loading...</div>

  const statusColor: Record<string, string> = {
    ACTIVE: 'bg-green-100 text-green-800',
    PENDING: 'bg-yellow-100 text-yellow-800',
    DETACHED: 'bg-gray-100 text-gray-800',
  }

  return (
    <div>
      <div className="flex items-center gap-4 mb-6">
        <div className="p-3 bg-primary/10 rounded-lg">
          <Server className="w-6 h-6 text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">{cluster.name || cluster.clusterId}</h1>
          <div className="flex items-center gap-2 mt-1">
            <code className="text-sm bg-gray-100 px-2 py-0.5 rounded font-mono text-gray-700">
              {cluster.clusterId}
            </code>
            <button onClick={copyClusterId} className="p-0.5 text-gray-400 hover:text-gray-600" title="Copy Cluster ID">
              {copied ? <Check className="w-3.5 h-3.5 text-green-600" /> : <Copy className="w-3.5 h-3.5" />}
            </button>
          </div>
        </div>
        <span className={`px-3 py-1 text-sm rounded-full ${statusColor[cluster.status] || 'bg-gray-100'}`}>
          {cluster.status}
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Bundles */}
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <Package className="w-5 h-5" /> Recent Bundles
          </h2>
          <div className="space-y-3">
            {bundles?.slice(0, 10).map((b: any) => (
              <div key={b.id} className="flex items-center justify-between py-2 border-b last:border-0">
                <div>
                  <p className="text-sm font-medium">{b.filename}</p>
                  <p className="text-xs text-muted-foreground">
                    {new Date(b.receivedAt).toLocaleString()}
                  </p>
                </div>
                <span className="text-xs text-muted-foreground">
                  {(b.sizeBytes / 1024 / 1024).toFixed(1)} MB
                </span>
              </div>
            ))}
            {(!bundles || bundles.length === 0) && (
              <p className="text-sm text-muted-foreground">No bundles received yet.</p>
            )}
          </div>
        </div>

        {/* Recommendations */}
        <div className="bg-white border rounded-lg p-6">
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <FileText className="w-5 h-5" /> Recommendations
          </h2>
          <div className="space-y-3">
            {recommendations?.map((r: any) => (
              <div key={r.id} className="border rounded-md p-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-medium">{r.title}</h3>
                  <span className={`px-2 py-0.5 text-xs rounded-full ${
                    r.severity === 'CRITICAL' ? 'bg-red-100 text-red-800' :
                    r.severity === 'WARNING' ? 'bg-yellow-100 text-yellow-800' :
                    'bg-blue-100 text-blue-800'
                  }`}>{r.severity}</span>
                </div>
                <p className="text-xs text-muted-foreground mt-1">{r.description}</p>
                <div className="flex items-center gap-2 mt-2">
                  <span className="text-xs px-2 py-0.5 bg-gray-100 rounded">{r.status}</span>
                  <span className="text-xs text-muted-foreground">{r.source}</span>
                </div>
              </div>
            ))}
            {(!recommendations || recommendations.length === 0) && (
              <p className="text-sm text-muted-foreground">No recommendations yet.</p>
            )}
          </div>
        </div>

        {/* Metrics Chart Placeholder */}
        <div className="bg-white border rounded-lg p-6 lg:col-span-2">
          <h2 className="text-lg font-semibold mb-4">Metrics Overview</h2>
          <div className="h-64">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={[
                { time: '00:00', cpu: 25, memory: 60 },
                { time: '04:00', cpu: 20, memory: 58 },
                { time: '08:00', cpu: 45, memory: 65 },
                { time: '12:00', cpu: 70, memory: 72 },
                { time: '16:00', cpu: 55, memory: 68 },
                { time: '20:00', cpu: 35, memory: 62 },
              ]}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="time" />
                <YAxis />
                <Tooltip />
                <Line type="monotone" dataKey="cpu" stroke="#3b82f6" name="CPU %" />
                <Line type="monotone" dataKey="memory" stroke="#10b981" name="Memory %" />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <p className="text-xs text-muted-foreground mt-2">
            Metrics will be populated from received bundle data.
          </p>
        </div>
      </div>
    </div>
  )
}
