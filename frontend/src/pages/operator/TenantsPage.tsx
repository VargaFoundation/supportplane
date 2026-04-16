import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { Building2, Server, Users, Ticket, Edit2, X, Save, Calendar } from 'lucide-react'
import { Card, Button, StatusBadge, CardSkeleton, EmptyState } from '@/components/ui'

const SUPPORT_LEVELS = ['BASIC', 'STANDARD', 'PREMIUM', 'CRITICAL']

export default function TenantsPage() {
  const queryClient = useQueryClient()
  const [editId, setEditId] = useState<string | null>(null)
  const [form, setForm] = useState<Record<string, any>>({})

  const { data: tenants, isLoading } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.get('/tenants').then((r) => r.data),
  })

  const updateMutation = useMutation({
    mutationFn: ({ tenantId, data }: { tenantId: string; data: any }) =>
      api.put(`/tenants/${tenantId}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants'] })
      setEditId(null)
    },
  })

  const startEdit = (t: any) => {
    setEditId(t.tenantId)
    setForm({
      name: t.name || '',
      clientName: t.clientName || '',
      supportLevel: t.supportLevel || '',
      contractReference: t.contractReference || '',
      contractFramework: t.contractFramework || '',
      contractEndDate: t.contractEndDate || '',
      notes: t.notes || '',
    })
  }

  const handleSave = (tenantId: string) => {
    updateMutation.mutate({ tenantId, data: form })
  }

  const inputCls = "w-full px-3 py-1.5 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"

  if (isLoading) {
    return (
      <div>
        <h1 className="text-2xl font-semibold tracking-tight mb-6">Tenants</h1>
        <div className="grid gap-4">
          {Array.from({ length: 3 }).map((_, i) => <CardSkeleton key={i} />)}
        </div>
      </div>
    )
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold tracking-tight mb-6">Tenants</h1>

      <div className="grid gap-4">
        {tenants?.map((t: any) => (
          <Card key={t.id} className="p-6">
            {/* Header */}
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-4">
                <div className="p-2 bg-primary/8 rounded-lg">
                  <Building2 className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h3 className="font-semibold">{t.name}</h3>
                  <p className="text-sm text-muted-foreground font-mono">{t.tenantId}</p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <StatusBadge value={t.active ? 'ACTIVE' : 'DETACHED'} />
                {t.licenseTier && <StatusBadge value={t.licenseTier} />}
                {editId !== t.tenantId ? (
                  <Button variant="ghost" size="sm" onClick={() => startEdit(t)}>
                    <Edit2 className="w-3.5 h-3.5" /> Edit
                  </Button>
                ) : (
                  <Button variant="ghost" size="sm" onClick={() => setEditId(null)}>
                    <X className="w-3.5 h-3.5" /> Cancel
                  </Button>
                )}
              </div>
            </div>

            {/* Counters */}
            <div className="grid grid-cols-3 gap-4 mb-4">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Server className="w-4 h-4" /> {t.clusterCount ?? 0} clusters
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Users className="w-4 h-4" /> {t.userCount ?? 0} users
              </div>
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Ticket className="w-4 h-4" /> {t.openTicketCount ?? 0} open tickets
              </div>
            </div>

            {/* Contract info (read-only) */}
            {editId !== t.tenantId && (t.clientName || t.contractReference || t.supportLevel || t.contractEndDate) && (
              <div className="border-t pt-4 grid grid-cols-2 lg:grid-cols-3 gap-3 text-sm">
                {t.clientName && <div><span className="text-muted-foreground">Client:</span> <span className="font-medium">{t.clientName}</span></div>}
                {t.supportLevel && <div><span className="text-muted-foreground">Support:</span> <StatusBadge value={t.supportLevel} /></div>}
                {t.contractReference && <div><span className="text-muted-foreground">Contract:</span> <span className="font-medium">{t.contractReference}</span></div>}
                {t.contractFramework && <div><span className="text-muted-foreground">Framework:</span> <span className="font-medium">{t.contractFramework}</span></div>}
                {t.contractEndDate && (
                  <div className="flex items-center gap-1">
                    <Calendar className="w-3.5 h-3.5 text-muted-foreground" />
                    <span className="text-muted-foreground">Ends:</span>
                    <span className="font-medium">{new Date(t.contractEndDate).toLocaleDateString()}</span>
                  </div>
                )}
                {t.notes && <div className="col-span-full"><span className="text-muted-foreground">Notes:</span> <span>{t.notes}</span></div>}
              </div>
            )}

            {/* Edit form */}
            {editId === t.tenantId && (
              <div className="border-t pt-4 space-y-3">
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Company Name</label>
                    <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })}
                      className={inputCls} />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Client Name</label>
                    <input value={form.clientName} onChange={(e) => setForm({ ...form, clientName: e.target.value })}
                      className={inputCls} placeholder="End customer name" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Support Level</label>
                    <select value={form.supportLevel} onChange={(e) => setForm({ ...form, supportLevel: e.target.value })}
                      className={inputCls}>
                      <option value="">Not set</option>
                      {SUPPORT_LEVELS.map((l) => <option key={l} value={l}>{l}</option>)}
                    </select>
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Contract Reference</label>
                    <input value={form.contractReference} onChange={(e) => setForm({ ...form, contractReference: e.target.value })}
                      className={inputCls} placeholder="e.g. CT-2026-0042" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Contract Framework</label>
                    <input value={form.contractFramework} onChange={(e) => setForm({ ...form, contractFramework: e.target.value })}
                      className={inputCls} placeholder="e.g. Contrat cadre XYZ" />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-muted-foreground mb-1">Contract End Date</label>
                    <input type="date" value={form.contractEndDate} onChange={(e) => setForm({ ...form, contractEndDate: e.target.value })}
                      className={inputCls} />
                  </div>
                </div>
                <div>
                  <label className="block text-xs font-medium text-muted-foreground mb-1">Notes</label>
                  <textarea value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })}
                    className={inputCls} rows={2} placeholder="Free-form notes about this tenant..." />
                </div>
                <div className="flex gap-2">
                  <Button size="sm" onClick={() => handleSave(t.tenantId)} loading={updateMutation.isPending}>
                    <Save className="w-3.5 h-3.5" /> Save
                  </Button>
                  <Button variant="secondary" size="sm" onClick={() => setEditId(null)}>Cancel</Button>
                </div>
              </div>
            )}
          </Card>
        ))}
        {(!tenants || tenants.length === 0) && (
          <EmptyState icon={Building2} title="No tenants registered"
            description="Tenants are created when companies register on the platform." />
        )}
      </div>
    </div>
  )
}
