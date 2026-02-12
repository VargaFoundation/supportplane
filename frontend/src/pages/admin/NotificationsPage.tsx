import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { Bell, Plus, Trash2 } from 'lucide-react'

export default function NotificationsPage() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [type, setType] = useState('BUNDLE_RECEIVED')
  const [channel, setChannel] = useState('EMAIL')
  const [target, setTarget] = useState('')

  const { data: configs, isLoading } = useQuery({
    queryKey: ['notification-configs'],
    queryFn: () => api.get('/notifications/config').then((r) => r.data),
  })

  const createMutation = useMutation({
    mutationFn: (data: { type: string; channel: string; config: Record<string, string> }) =>
      api.post('/notifications/config', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notification-configs'] })
      setShowCreate(false)
      setTarget('')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/notifications/config/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notification-configs'] }),
  })

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault()
    const config: Record<string, string> = {}
    if (channel === 'EMAIL') config.email = target
    else if (channel === 'WEBHOOK') config.url = target
    else if (channel === 'SLACK') config.webhookUrl = target
    createMutation.mutate({ type, channel, config })
  }

  const eventTypes = [
    'BUNDLE_RECEIVED',
    'TICKET_CREATED',
    'TICKET_UPDATED',
    'RECOMMENDATION_DELIVERED',
    'CLUSTER_ATTACHED',
    'CLUSTER_DETACHED',
    'LICENSE_EXPIRING',
  ]

  const channelColor: Record<string, string> = {
    EMAIL: 'bg-blue-100 text-blue-800',
    WEBHOOK: 'bg-green-100 text-green-800',
    SLACK: 'bg-purple-100 text-purple-800',
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Notifications</h1>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" /> Add Rule
        </button>
      </div>

      {showCreate && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <h2 className="text-lg font-semibold mb-4">Create Notification Rule</h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Event Type</label>
                <select
                  value={type} onChange={(e) => setType(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  {eventTypes.map((t) => (
                    <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Channel</label>
                <select
                  value={channel} onChange={(e) => setChannel(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm"
                >
                  <option value="EMAIL">Email</option>
                  <option value="WEBHOOK">Webhook</option>
                  <option value="SLACK">Slack</option>
                </select>
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">
                {channel === 'EMAIL' ? 'Email Address' : channel === 'SLACK' ? 'Slack Webhook URL' : 'Webhook URL'}
              </label>
              <input
                type="text" value={target} onChange={(e) => setTarget(e.target.value)}
                placeholder={channel === 'EMAIL' ? 'user@example.com' : 'https://...'}
                className="w-full px-3 py-2 border rounded-md text-sm" required
              />
            </div>
            <div className="flex gap-2">
              <button type="submit" className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                Create
              </button>
              <button type="button" onClick={() => setShowCreate(false)}
                className="px-4 py-2 border rounded-md text-sm">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {isLoading ? (
        <div className="text-muted-foreground">Loading notification rules...</div>
      ) : (
        <div className="grid gap-4">
          {configs?.map((c: any) => (
            <div key={c.id} className="bg-white border rounded-lg p-5 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="p-2 bg-primary/10 rounded-lg">
                  <Bell className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <h3 className="text-sm font-medium">{c.type?.replace(/_/g, ' ')}</h3>
                  <p className="text-xs text-muted-foreground mt-0.5">
                    {c.config?.email || c.config?.url || c.config?.webhookUrl || '-'}
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span className={`px-2 py-0.5 text-xs rounded-full ${channelColor[c.channel] || 'bg-gray-100'}`}>
                  {c.channel}
                </span>
                <button
                  onClick={() => deleteMutation.mutate(c.id)}
                  className="p-2 text-muted-foreground hover:text-destructive"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
          {(!configs || configs.length === 0) && (
            <div className="text-center py-12 text-muted-foreground">
              No notification rules configured. Click "Add Rule" to get started.
            </div>
          )}
        </div>
      )}
    </div>
  )
}
