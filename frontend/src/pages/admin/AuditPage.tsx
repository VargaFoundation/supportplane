import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { History } from 'lucide-react'
import { Card, TableSkeleton, EmptyState } from '@/components/ui'

export default function AuditPage() {
  const { data: events, isLoading } = useQuery({
    queryKey: ['audit-events'],
    queryFn: () => api.get('/audit').then((r) => r.data),
    refetchInterval: 30000,
  })

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-semibold tracking-tight">Activity Log</h1>
        <p className="text-sm text-muted-foreground mt-0.5">Recent actions across the platform</p>
      </div>

      {isLoading ? (
        <TableSkeleton rows={10} cols={5} />
      ) : !events || events.length === 0 ? (
        <EmptyState icon={History} title="No activity yet"
          description="Actions like ticket creation, user management, and tenant updates will appear here." />
      ) : (
        <Card className="overflow-hidden">
          <table className="w-full">
            <thead>
              <tr className="border-b bg-gray-50/80">
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Time</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Action</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Target</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Actor</th>
                <th className="text-left p-3 text-xs font-medium text-muted-foreground uppercase tracking-wider">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {events.map((e: any) => (
                <tr key={e.id} className="hover:bg-gray-50/60 transition-colors">
                  <td className="p-3 text-xs text-muted-foreground whitespace-nowrap">
                    {new Date(e.createdAt).toLocaleString()}
                  </td>
                  <td className="p-3">
                    <ActionBadge action={e.action} />
                  </td>
                  <td className="p-3">
                    <div className="text-sm font-medium">{e.targetLabel}</div>
                    <div className="text-xs text-muted-foreground">{e.targetType} #{e.targetId}</div>
                  </td>
                  <td className="p-3 text-sm text-muted-foreground">
                    {e.actor}{e.actorRole ? ` (${e.actorRole})` : ''}
                  </td>
                  <td className="p-3 text-xs text-muted-foreground max-w-[200px] truncate" title={e.details}>
                    {e.details}
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

function ActionBadge({ action }: { action: string }) {
  const colors: Record<string, string> = {
    TICKET_CREATED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    TICKET_STATUS_CHANGED: 'bg-blue-50 text-blue-700 border-blue-200',
    TICKET_ASSIGNED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    USER_CREATED: 'bg-purple-50 text-purple-700 border-purple-200',
    USER_DEACTIVATED: 'bg-red-50 text-red-700 border-red-200',
    TENANT_UPDATED: 'bg-amber-50 text-amber-700 border-amber-200',
  }
  const label = action.replace(/_/g, ' ')
  return (
    <span className={`inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border ${colors[action] || 'bg-gray-50 text-gray-600 border-gray-200'}`}>
      {label}
    </span>
  )
}
