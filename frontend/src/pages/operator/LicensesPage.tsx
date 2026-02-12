import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { Award, Plus, Edit2 } from 'lucide-react'

export default function LicensesPage() {
  const queryClient = useQueryClient()
  const [editId, setEditId] = useState<number | null>(null)
  const [tier, setTier] = useState('STANDARD')
  const [maxClusters, setMaxClusters] = useState(5)
  const [maxUsers, setMaxUsers] = useState(10)
  const [validUntil, setValidUntil] = useState('')
  const [editTenantId, setEditTenantId] = useState<number | null>(null)

  const { data: licenses, isLoading } = useQuery({
    queryKey: ['licenses'],
    queryFn: () => api.get('/licenses').then((r) => r.data),
  })

  const { data: tenants } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.get('/tenants').then((r) => r.data),
  })

  const updateMutation = useMutation({
    mutationFn: (data: { id: number; tier: string; maxClusters: number; maxUsers: number; validUntil: string }) =>
      api.put(`/licenses/${data.id}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['licenses'] })
      setEditId(null)
    },
  })

  const createMutation = useMutation({
    mutationFn: (data: { tenantId: number; tier: string; maxClusters: number; maxUsers: number; validUntil: string }) =>
      api.post('/licenses', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['licenses'] })
      setEditId(null)
      setEditTenantId(null)
    },
  })

  const startEdit = (license: any) => {
    setEditId(license.id)
    setTier(license.tier)
    setMaxClusters(license.maxClusters)
    setMaxUsers(license.maxUsers)
    setValidUntil(license.validUntil?.split('T')[0] || '')
  }

  const startCreate = () => {
    setEditId(-1)
    setEditTenantId(null)
    setTier('STANDARD')
    setMaxClusters(5)
    setMaxUsers(10)
    setValidUntil('')
  }

  const handleSave = () => {
    if (editId === -1 && editTenantId) {
      createMutation.mutate({ tenantId: editTenantId, tier, maxClusters, maxUsers, validUntil })
    } else if (editId && editId > 0) {
      updateMutation.mutate({ id: editId, tier, maxClusters, maxUsers, validUntil })
    }
  }

  const tierColor: Record<string, string> = {
    BASIC: 'bg-gray-100 text-gray-800',
    STANDARD: 'bg-blue-100 text-blue-800',
    PREMIUM: 'bg-purple-100 text-purple-800',
    ENTERPRISE: 'bg-orange-100 text-orange-800',
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Licenses</h1>
        <button
          onClick={startCreate}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" /> Add License
        </button>
      </div>

      {editId !== null && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">
            {editId === -1 ? 'Create License' : 'Edit License'}
          </h2>
          <div className="space-y-4">
            {editId === -1 && (
              <div>
                <label className="block text-sm font-medium mb-1">Tenant</label>
                <select
                  value={editTenantId || ''} onChange={(e) => setEditTenantId(Number(e.target.value))}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="">Select tenant...</option>
                  {tenants?.map((t: any) => (
                    <option key={t.id} value={t.id}>{t.name} ({t.tenantId})</option>
                  ))}
                </select>
              </div>
            )}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Tier</label>
                <select
                  value={tier} onChange={(e) => setTier(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="BASIC">Basic</option>
                  <option value="STANDARD">Standard</option>
                  <option value="PREMIUM">Premium</option>
                  <option value="ENTERPRISE">Enterprise</option>
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Max Clusters</label>
                <input
                  type="number" value={maxClusters} onChange={(e) => setMaxClusters(Number(e.target.value))}
                  className="w-full px-3 py-2 border rounded-md text-sm" min={1}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Max Users</label>
                <input
                  type="number" value={maxUsers} onChange={(e) => setMaxUsers(Number(e.target.value))}
                  className="w-full px-3 py-2 border rounded-md text-sm" min={1}
                />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Valid Until</label>
                <input
                  type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                />
              </div>
            </div>
            <div className="flex gap-2">
              <button onClick={handleSave}
                className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                Save
              </button>
              <button onClick={() => setEditId(null)}
                className="px-4 py-2 border rounded-md text-sm">Cancel</button>
            </div>
          </div>
        </div>
      )}

      {isLoading ? (
        <div className="text-muted-foreground">Loading licenses...</div>
      ) : (
        <div className="bg-white border rounded-lg overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Tenant</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Tier</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Clusters</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Users</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Valid Until</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground">Status</th>
                <th className="text-right p-3 text-xs font-medium text-muted-foreground">Actions</th>
              </tr>
            </thead>
            <tbody>
              {licenses?.map((l: any) => {
                const expired = l.validUntil && new Date(l.validUntil) < new Date()
                const expiringSoon = l.validUntil && !expired &&
                  new Date(l.validUntil).getTime() - Date.now() < 30 * 24 * 60 * 60 * 1000
                return (
                  <tr key={l.id} className="border-b last:border-0 hover:bg-gray-50">
                    <td className="p-3">
                      <div className="flex items-center gap-3">
                        <Award className="w-4 h-4 text-primary" />
                        <div>
                          <p className="text-sm font-medium">{l.tenantName}</p>
                          <p className="text-xs text-muted-foreground font-mono">{l.tenantSlug}</p>
                        </div>
                      </div>
                    </td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 text-xs rounded-full ${tierColor[l.tier] || 'bg-gray-100'}`}>
                        {l.tier}
                      </span>
                    </td>
                    <td className="p-3 text-sm">{l.usedClusters ?? '-'} / {l.maxClusters}</td>
                    <td className="p-3 text-sm">{l.usedUsers ?? '-'} / {l.maxUsers}</td>
                    <td className="p-3 text-sm text-muted-foreground">
                      {l.validUntil ? new Date(l.validUntil).toLocaleDateString() : 'Unlimited'}
                    </td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 text-xs rounded-full ${
                        expired ? 'bg-red-100 text-red-800' :
                        expiringSoon ? 'bg-yellow-100 text-yellow-800' :
                        'bg-green-100 text-green-800'
                      }`}>
                        {expired ? 'Expired' : expiringSoon ? 'Expiring Soon' : 'Active'}
                      </span>
                    </td>
                    <td className="p-3 text-right">
                      <button onClick={() => startEdit(l)}
                        className="p-2 text-muted-foreground hover:text-primary">
                        <Edit2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                )
              })}
              {(!licenses || licenses.length === 0) && (
                <tr>
                  <td colSpan={7} className="p-8 text-center text-muted-foreground text-sm">
                    No licenses configured.
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
