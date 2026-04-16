import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { Users, UserPlus, Trash2 } from 'lucide-react'
import { Button, Card, TableSkeleton, EmptyState } from '@/components/ui'
import { cn } from '@/lib/utils'

const ROLE_STYLES: Record<string, string> = {
  ADMIN: 'bg-purple-50 text-purple-700 border-purple-200',
  USER: 'bg-blue-50 text-blue-700 border-blue-200',
  OPERATOR: 'bg-amber-50 text-amber-700 border-amber-200',
}

export default function UsersPage() {
  const queryClient = useQueryClient()
  const userRole = useAuthStore((s) => s.role)
  const isOperator = userRole === 'OPERATOR'
  const [showCreate, setShowCreate] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [role, setRole] = useState('USER')
  const [targetTenant, setTargetTenant] = useState('')

  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get('/users').then((r) => r.data),
  })

  const { data: tenants } = useQuery({
    queryKey: ['tenants'],
    queryFn: () => api.get('/tenants').then((r) => r.data),
    enabled: isOperator,
  })

  const createMutation = useMutation({
    mutationFn: (data: { email: string; fullName: string; password: string; role: string; tenantId?: string }) =>
      api.post('/users', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setShowCreate(false)
      setEmail('')
      setFullName('')
      setPassword('')
      setRole('USER')
      setTargetTenant('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/users/${id}`),
    onSuccess: () => {
      setConfirmDelete(null)
      queryClient.invalidateQueries({ queryKey: ['users'] })
    },
  })

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    createMutation.mutate({
      email, fullName, password, role,
      ...(isOperator && targetTenant ? { tenantId: targetTenant } : {}),
    })
  }

  const inputCls = "w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"
  const activeUsers = users?.filter((u: any) => u.active !== false) || []

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Users</h1>
          {activeUsers.length > 0 && (
            <p className="text-sm text-muted-foreground mt-0.5">{activeUsers.length} active member{activeUsers.length !== 1 ? 's' : ''}</p>
          )}
        </div>
        <Button onClick={() => setShowCreate(true)}>
          <UserPlus className="w-4 h-4" /> Add User
        </Button>
      </div>

      {showCreate && (
        <Card className="p-6 mb-6">
          <h2 className="text-base font-semibold mb-4">{isOperator ? 'Add user to a tenant' : 'Invite a new user'}</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            {isOperator && (
              <div>
                <label className="block text-sm font-medium mb-1.5">Target Tenant</label>
                <select value={targetTenant} onChange={(e) => setTargetTenant(e.target.value)}
                  className={inputCls} required>
                  <option value="">Select a tenant...</option>
                  {tenants?.map((t: any) => (
                    <option key={t.tenantId} value={t.tenantId}>{t.name} ({t.tenantId})</option>
                  ))}
                </select>
              </div>
            )}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Full Name</label>
                <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)}
                  className={inputCls} required placeholder="John Doe" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Email</label>
                <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
                  className={inputCls} required placeholder="john@company.com" />
              </div>
            </div>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1.5">Temporary Password</label>
                <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                  className={inputCls} required minLength={8} placeholder="Min. 8 characters" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1.5">Role</label>
                <select value={role} onChange={(e) => setRole(e.target.value)} className={inputCls}>
                  <option value="USER">User</option>
                  <option value="ADMIN">Admin</option>
                </select>
              </div>
            </div>
            {createMutation.isError && (
              <div className="text-sm text-destructive bg-destructive/5 border border-destructive/10 p-3 rounded-lg">
                {(createMutation.error as any)?.response?.data?.error || 'Failed to create user. Check license limits.'}
              </div>
            )}
            <div className="flex gap-2">
              <Button type="submit" loading={createMutation.isPending}>Create User</Button>
              <Button variant="secondary" type="button" onClick={() => setShowCreate(false)}>Cancel</Button>
            </div>
          </form>
        </Card>
      )}

      {isLoading ? (
        <TableSkeleton rows={4} cols={5} />
      ) : activeUsers.length === 0 ? (
        <EmptyState icon={Users} title="No users yet"
          description="Add team members to give them access to this tenant."
          action={<Button onClick={() => setShowCreate(true)}><UserPlus className="w-4 h-4" /> Add User</Button>}
        />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50/80">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">User</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Email</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Role</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Joined</th>
                <th className="w-12"></th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {activeUsers.map((u: any) => (
                <tr key={u.id} className="hover:bg-gray-50/60 transition-colors">
                  <td className="p-3">
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-full bg-primary/8 flex items-center justify-center text-xs font-medium text-primary">
                        {u.fullName?.split(' ').map((n: string) => n[0]).join('').slice(0, 2).toUpperCase() || '?'}
                      </div>
                      <span className="text-sm font-medium">{u.fullName}</span>
                    </div>
                  </td>
                  <td className="p-3 text-sm text-muted-foreground">{u.email}</td>
                  <td className="p-3">
                    <span className={cn(
                      "inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border",
                      ROLE_STYLES[u.role] || 'bg-gray-50 text-gray-600 border-gray-200',
                    )}>
                      {u.role}
                    </span>
                  </td>
                  <td className="p-3 text-xs text-muted-foreground">
                    {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '-'}
                  </td>
                  <td className="p-3">
                    {confirmDelete === u.id ? (
                      <div className="flex items-center gap-1">
                        <Button size="sm" variant="destructive" onClick={() => deleteMutation.mutate(u.id)}>Remove</Button>
                        <Button size="sm" variant="secondary" onClick={() => setConfirmDelete(null)}>No</Button>
                      </div>
                    ) : (
                      <button
                        onClick={() => setConfirmDelete(u.id)}
                        className="p-1.5 text-muted-foreground hover:text-destructive transition-colors rounded-md hover:bg-red-50"
                        title="Remove user" aria-label="Remove user"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    )}
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
