import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { Ticket, MessageSquare, UserPlus, Clock, AlertTriangle, Paperclip } from 'lucide-react'
import { Card, Button, StatusBadge, PriorityBadge, PageLoader } from '@/components/ui'

function SlaIndicator({ deadline, remaining, breached }: { deadline?: string; remaining?: number; breached?: boolean }) {
  if (!deadline) return null
  const isBreached = breached || (remaining != null && remaining < 0)
  const hours = remaining != null ? Math.floor(Math.abs(remaining) / 60) : null
  const mins = remaining != null ? Math.abs(remaining) % 60 : null

  return (
    <div className={`p-3 rounded-lg border ${isBreached ? 'bg-red-50 border-red-200' : remaining != null && remaining < 120 ? 'bg-amber-50 border-amber-200' : 'bg-gray-50 border-gray-200'}`}>
      <div className="flex items-center gap-2 mb-1">
        {isBreached ? <AlertTriangle className="w-4 h-4 text-red-600" /> : <Clock className="w-4 h-4 text-muted-foreground" />}
        <span className="text-xs font-medium text-muted-foreground">SLA</span>
      </div>
      {isBreached ? (
        <p className="text-sm font-semibold text-red-700">Breached {hours != null ? `${hours}h${mins}m ago` : ''}</p>
      ) : remaining != null ? (
        <p className="text-sm font-semibold">{hours}h {mins}m remaining</p>
      ) : null}
      <p className="text-xs text-muted-foreground mt-0.5">Deadline: {new Date(deadline).toLocaleString()}</p>
    </div>
  )
}

export default function TicketDetailPage() {
  const { id } = useParams()
  const role = useAuthStore((s) => s.role)
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [assignEmail, setAssignEmail] = useState('')

  const { data: ticket, isLoading } = useQuery({
    queryKey: ['ticket', id],
    queryFn: () => api.get(`/tickets/${id}`).then((r) => r.data),
  })

  const { data: comments } = useQuery({
    queryKey: ['ticket-comments', id],
    queryFn: () => api.get(`/tickets/${id}/comments`).then((r) => r.data),
  })

  const commentMutation = useMutation({
    mutationFn: (content: string) =>
      api.post(`/tickets/${id}/comments`, { content }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket-comments', id] })
      setComment('')
    },
  })

  const assignMutation = useMutation({
    mutationFn: (email: string) =>
      api.put(`/tickets/${id}/assign`, { assignedTo: email }).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ticket', id] })
      setAssignEmail('')
    },
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) =>
      api.put(`/tickets/${id}`, { status }).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['ticket', id] }),
  })

  if (isLoading || !ticket) return <PageLoader message="Loading ticket..." />

  const statusTransitions: Record<string, string[]> = {
    OPEN: ['IN_PROGRESS', 'CLOSED'],
    IN_PROGRESS: ['WAITING', 'RESOLVED', 'CLOSED'],
    WAITING: ['IN_PROGRESS', 'CLOSED'],
    RESOLVED: ['CLOSED', 'OPEN'],
    CLOSED: ['OPEN'],
    ASSIGNED: ['IN_PROGRESS', 'CLOSED'],
  }

  const inputCls = "w-full px-3 py-2 border rounded-md text-sm focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors"

  return (
    <div>
      <div className="flex items-center gap-4 mb-6">
        <div className="p-3 bg-primary/8 rounded-lg">
          <Ticket className="w-6 h-6 text-primary" />
        </div>
        <div className="flex-1">
          <h1 className="text-xl font-semibold tracking-tight">{ticket.title}</h1>
          <p className="text-sm text-muted-foreground">
            Created {new Date(ticket.createdAt).toLocaleString()}
            {ticket.createdByName && ` by ${ticket.createdByName}`}
          </p>
        </div>
        <StatusBadge value={ticket.status} />
        <PriorityBadge value={ticket.priority} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Description */}
          <Card className="p-6">
            <h2 className="text-sm font-semibold text-muted-foreground mb-3">Description</h2>
            <p className="text-sm whitespace-pre-wrap leading-relaxed">{ticket.description}</p>
          </Card>

          {/* Comments */}
          <Card className="p-6">
            <h2 className="text-base font-semibold flex items-center gap-2 mb-4">
              <MessageSquare className="w-4 h-4 text-primary" /> Comments ({comments?.length || 0})
            </h2>
            <div className="space-y-3 mb-6">
              {comments?.map((c: any) => (
                <div key={c.id} className="border rounded-lg p-4 bg-gray-50/50">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">{c.authorName || 'System'}</span>
                    <span className="text-xs text-muted-foreground">
                      {new Date(c.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <p className="text-sm whitespace-pre-wrap">{c.content}</p>
                  {c.attachmentFilename && (
                    <div className="mt-2 flex items-center gap-1 text-xs text-primary">
                      <Paperclip className="w-3 h-3" />
                      <a href={`/api/v1/tickets/comments/${c.id}/attachment`}
                        className="hover:underline">{c.attachmentFilename}</a>
                      <span className="text-muted-foreground">({Math.round((c.attachmentSize || 0) / 1024)} KB)</span>
                    </div>
                  )}
                </div>
              ))}
              {(!comments || comments.length === 0) && (
                <p className="text-sm text-muted-foreground text-center py-4">No comments yet.</p>
              )}
            </div>
            <form
              onSubmit={(e) => {
                e.preventDefault()
                if (comment.trim()) commentMutation.mutate(comment)
              }}
              className="space-y-3"
            >
              <textarea
                value={comment} onChange={(e) => setComment(e.target.value)}
                placeholder="Write a comment..."
                className={inputCls} rows={3}
              />
              <Button type="submit" loading={commentMutation.isPending} disabled={!comment.trim()}>
                Add Comment
              </Button>
            </form>
          </Card>
        </div>

        {/* Sidebar */}
        <div className="space-y-4">
          {/* SLA */}
          <SlaIndicator
            deadline={ticket.slaDeadline}
            remaining={ticket.slaRemainingMinutes}
            breached={ticket.slaBreached}
          />

          {/* Details */}
          <Card className="p-5">
            <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Details</h2>
            <dl className="space-y-3">
              <div>
                <dt className="text-xs text-muted-foreground">Cluster</dt>
                <dd className="text-sm font-medium">{ticket.clusterName || '-'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Assigned To</dt>
                <dd className="text-sm font-medium">{ticket.assignedToName || <span className="italic text-muted-foreground">Unassigned</span>}</dd>
              </div>
              {ticket.tenantName && (
                <div>
                  <dt className="text-xs text-muted-foreground">Tenant</dt>
                  <dd className="text-sm font-medium">{ticket.tenantName}</dd>
                </div>
              )}
              <div>
                <dt className="text-xs text-muted-foreground">Last Updated</dt>
                <dd className="text-xs text-muted-foreground">{new Date(ticket.updatedAt).toLocaleString()}</dd>
              </div>
            </dl>
          </Card>

          {/* Status Actions */}
          <Card className="p-5">
            <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3">Status Actions</h2>
            <div className="space-y-1.5">
              {statusTransitions[ticket.status]?.map((s: string) => (
                <Button
                  key={s}
                  variant="secondary"
                  size="sm"
                  className="w-full justify-start"
                  onClick={() => statusMutation.mutate(s)}
                  loading={statusMutation.isPending}
                >
                  Move to {s.replace(/_/g, ' ')}
                </Button>
              ))}
            </div>
          </Card>

          {/* Assign (operator/admin) */}
          {(role === 'OPERATOR' || role === 'ADMIN') && (
            <Card className="p-5">
              <h2 className="text-xs font-semibold text-muted-foreground uppercase tracking-wider mb-3 flex items-center gap-1.5">
                <UserPlus className="w-3.5 h-3.5" /> Assign
              </h2>
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  if (assignEmail) assignMutation.mutate(assignEmail)
                }}
                className="space-y-2"
              >
                <input
                  type="text" value={assignEmail} onChange={(e) => setAssignEmail(e.target.value)}
                  placeholder="User email" className={inputCls}
                />
                <Button type="submit" size="sm" className="w-full" loading={assignMutation.isPending}>
                  Assign
                </Button>
              </form>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
