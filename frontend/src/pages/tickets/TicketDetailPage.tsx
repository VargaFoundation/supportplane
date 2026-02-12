import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { useAuthStore } from '@/stores/auth'
import { Ticket, MessageSquare, UserPlus } from 'lucide-react'

export default function TicketDetailPage() {
  const { id } = useParams()
  const role = useAuthStore((s) => s.role)
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [assignEmail, setAssignEmail] = useState('')

  const { data: ticket } = useQuery({
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

  if (!ticket) return <div className="text-muted-foreground">Loading...</div>

  const priorityColor: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800',
    HIGH: 'bg-orange-100 text-orange-800',
    MEDIUM: 'bg-yellow-100 text-yellow-800',
    LOW: 'bg-blue-100 text-blue-800',
  }

  const statusColor: Record<string, string> = {
    OPEN: 'bg-green-100 text-green-800',
    IN_PROGRESS: 'bg-blue-100 text-blue-800',
    WAITING: 'bg-yellow-100 text-yellow-800',
    RESOLVED: 'bg-gray-100 text-gray-800',
    CLOSED: 'bg-gray-100 text-gray-600',
  }

  const statusTransitions: Record<string, string[]> = {
    OPEN: ['IN_PROGRESS', 'CLOSED'],
    IN_PROGRESS: ['WAITING', 'RESOLVED', 'CLOSED'],
    WAITING: ['IN_PROGRESS', 'CLOSED'],
    RESOLVED: ['CLOSED', 'OPEN'],
    CLOSED: ['OPEN'],
  }

  return (
    <div>
      <div className="flex items-center gap-4 mb-6">
        <div className="p-3 bg-primary/10 rounded-lg">
          <Ticket className="w-6 h-6 text-primary" />
        </div>
        <div className="flex-1">
          <h1 className="text-2xl font-bold">{ticket.title}</h1>
          <p className="text-sm text-muted-foreground">
            Created {new Date(ticket.createdAt).toLocaleString()}
            {ticket.createdByName && ` by ${ticket.createdByName}`}
          </p>
        </div>
        <span className={`px-3 py-1 text-sm rounded-full ${statusColor[ticket.status] || 'bg-gray-100'}`}>
          {ticket.status?.replace('_', ' ')}
        </span>
        <span className={`px-3 py-1 text-sm rounded-full ${priorityColor[ticket.priority] || 'bg-gray-100'}`}>
          {ticket.priority}
        </span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Description */}
          <div className="bg-white border rounded-lg p-6">
            <h2 className="text-sm font-semibold text-muted-foreground mb-3">Description</h2>
            <p className="text-sm whitespace-pre-wrap">{ticket.description}</p>
          </div>

          {/* Comments */}
          <div className="bg-white border rounded-lg p-6">
            <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
              <MessageSquare className="w-5 h-5" /> Comments
            </h2>
            <div className="space-y-4 mb-6">
              {comments?.map((c: any) => (
                <div key={c.id} className="border rounded-md p-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-sm font-medium">{c.authorName || 'Unknown'}</span>
                    <span className="text-xs text-muted-foreground">
                      {new Date(c.createdAt).toLocaleString()}
                    </span>
                  </div>
                  <p className="text-sm whitespace-pre-wrap">{c.content}</p>
                </div>
              ))}
              {(!comments || comments.length === 0) && (
                <p className="text-sm text-muted-foreground">No comments yet.</p>
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
                className="w-full px-3 py-2 border rounded-md text-sm" rows={3}
              />
              <button type="submit"
                className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
                disabled={!comment.trim()}
              >
                Add Comment
              </button>
            </form>
          </div>
        </div>

        {/* Sidebar */}
        <div className="space-y-6">
          {/* Details */}
          <div className="bg-white border rounded-lg p-6">
            <h2 className="text-sm font-semibold text-muted-foreground mb-3">Details</h2>
            <dl className="space-y-3">
              <div>
                <dt className="text-xs text-muted-foreground">Cluster</dt>
                <dd className="text-sm">{ticket.clusterName || '-'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Assigned To</dt>
                <dd className="text-sm">{ticket.assignedToName || 'Unassigned'}</dd>
              </div>
              {ticket.tenantName && (
                <div>
                  <dt className="text-xs text-muted-foreground">Tenant</dt>
                  <dd className="text-sm">{ticket.tenantName}</dd>
                </div>
              )}
            </dl>
          </div>

          {/* Status Actions */}
          <div className="bg-white border rounded-lg p-6">
            <h2 className="text-sm font-semibold text-muted-foreground mb-3">Actions</h2>
            <div className="space-y-2">
              {statusTransitions[ticket.status]?.map((s: string) => (
                <button
                  key={s}
                  onClick={() => statusMutation.mutate(s)}
                  className="w-full text-left px-3 py-2 text-sm border rounded-md hover:bg-gray-50"
                >
                  Move to {s.replace('_', ' ')}
                </button>
              ))}
            </div>
          </div>

          {/* Assign (operator only) */}
          {role === 'OPERATOR' && (
            <div className="bg-white border rounded-lg p-6">
              <h2 className="text-sm font-semibold text-muted-foreground mb-3 flex items-center gap-2">
                <UserPlus className="w-4 h-4" /> Assign
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
                  placeholder="User email" className="w-full px-3 py-2 border rounded-md text-sm"
                />
                <button type="submit" className="w-full px-3 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                  Assign
                </button>
              </form>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
