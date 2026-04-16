import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import {
  HardDrive, Cpu, Database, Layers, Key, Lock, Shield,
  Globe, Server, Settings, Play, ArrowLeft, FileText, Loader2
} from 'lucide-react'

const COMPONENT_CONFIG: Record<string, {
  icon: React.ElementType
  tier: 'security' | 'core' | 'infra'
  label: string
}> = {
  Kerberos: { icon: Key, tier: 'security', label: 'Kerberos' },
  Ranger: { icon: Lock, tier: 'security', label: 'Ranger' },
  Knox: { icon: Shield, tier: 'security', label: 'Knox' },
  HDFS: { icon: HardDrive, tier: 'core', label: 'HDFS' },
  Yarn: { icon: Cpu, tier: 'core', label: 'YARN' },
  Hive: { icon: Database, tier: 'core', label: 'Hive' },
  HBase: { icon: Layers, tier: 'core', label: 'HBase' },
  Network: { icon: Globe, tier: 'infra', label: 'Network' },
  OS: { icon: Server, tier: 'infra', label: 'OS / Hardware' },
  Ambari: { icon: Settings, tier: 'infra', label: 'Ambari' },
  Platform: { icon: Server, tier: 'infra', label: 'Platform' },
}

const TIER_LABELS: Record<string, string> = {
  security: 'Security',
  core: 'Core Services',
  infra: 'Infrastructure',
}

const TIER_ORDER = ['security', 'core', 'infra']

export default function ClusterArchitecturePage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: cluster } = useQuery({
    queryKey: ['cluster', id],
    queryFn: () => api.get(`/clusters/${id}`).then((r) => r.data),
  })

  const { data: components, isLoading } = useQuery({
    queryKey: ['component-summary', id],
    queryFn: () => api.get(`/clusters/${id}/component-summary`).then((r) => r.data),
  })

  const { data: auditRuns } = useQuery({
    queryKey: ['audit-runs', id],
    queryFn: () => api.get(`/clusters/${id}/audit-runs`).then((r) => r.data),
  })

  const evaluateMutation = useMutation({
    mutationFn: () => api.post(`/clusters/${id}/evaluate`).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['component-summary', id] })
      queryClient.invalidateQueries({ queryKey: ['audit-runs', id] })
      queryClient.invalidateQueries({ queryKey: ['cluster-recommendations', id] })
    },
  })

  const componentMap = new Map<string, any>()
  components?.forEach((c: any) => componentMap.set(c.component, c))

  const lastRun = auditRuns?.[0]

  // Group components by tier
  const tiers = TIER_ORDER.map(tier => ({
    key: tier,
    label: TIER_LABELS[tier],
    components: Object.entries(COMPONENT_CONFIG)
      .filter(([, cfg]) => cfg.tier === tier)
      .map(([key, cfg]) => ({
        key,
        ...cfg,
        summary: componentMap.get(key),
      })),
  }))

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(`/clusters/${id}`)}
            className="p-2 hover:bg-gray-100 rounded-md">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold">
              {cluster?.name || cluster?.clusterId || 'Cluster'} — Architecture
            </h1>
            <p className="text-sm text-muted-foreground">
              Visual overview of component health and audit findings
            </p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <Link to={`/clusters/${id}/audit-report`}
            className="flex items-center gap-2 px-4 py-2 border rounded-md text-sm hover:bg-gray-50">
            <FileText className="w-4 h-4" /> View Report
          </Link>
          <button
            onClick={() => evaluateMutation.mutate()}
            disabled={evaluateMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90 disabled:opacity-50"
          >
            {evaluateMutation.isPending
              ? <Loader2 className="w-4 h-4 animate-spin" />
              : <Play className="w-4 h-4" />
            }
            Run Audit
          </button>
        </div>
      </div>

      {/* Last run info */}
      {lastRun && (
        <div className="bg-white border rounded-lg p-4 mb-6 flex items-center justify-between">
          <div className="flex items-center gap-4 text-sm">
            <span className="text-muted-foreground">Last audit:</span>
            <span>{new Date(lastRun.startedAt).toLocaleString()}</span>
            <span className={`px-2 py-0.5 text-xs rounded-full ${
              lastRun.status === 'COMPLETED' ? 'bg-green-100 text-green-800' :
              lastRun.status === 'FAILED' ? 'bg-red-100 text-red-800' :
              'bg-yellow-100 text-yellow-800'
            }`}>{lastRun.status}</span>
            <span>{lastRun.rulesEvaluated} rules evaluated</span>
            <span>{lastRun.findingsCount} findings</span>
          </div>
          {lastRun.summary && (
            <div className="flex items-center gap-3">
              {lastRun.summary.OK > 0 && (
                <span className="flex items-center gap-1 text-xs">
                  <span className="w-3 h-3 rounded-full bg-green-500" />
                  {lastRun.summary.OK} OK
                </span>
              )}
              {lastRun.summary.WARNING > 0 && (
                <span className="flex items-center gap-1 text-xs">
                  <span className="w-3 h-3 rounded-full bg-yellow-500" />
                  {lastRun.summary.WARNING} Warning
                </span>
              )}
              {lastRun.summary.CRITICAL > 0 && (
                <span className="flex items-center gap-1 text-xs">
                  <span className="w-3 h-3 rounded-full bg-red-500" />
                  {lastRun.summary.CRITICAL} Critical
                </span>
              )}
              {lastRun.summary.UNKNOWN > 0 && (
                <span className="flex items-center gap-1 text-xs">
                  <span className="w-3 h-3 rounded-full bg-gray-400" />
                  {lastRun.summary.UNKNOWN} Unknown
                </span>
              )}
            </div>
          )}
        </div>
      )}

      {/* Architecture grid */}
      {isLoading ? (
        <div className="text-muted-foreground text-center py-12">Loading component data...</div>
      ) : !components || components.length === 0 ? (
        <div className="text-center py-16">
          <Server className="w-12 h-12 text-gray-300 mx-auto mb-4" />
          <p className="text-muted-foreground mb-2">No audit data yet</p>
          <p className="text-sm text-muted-foreground">Run an audit to see the architecture overview</p>
        </div>
      ) : (
        <div className="space-y-6">
          {tiers.map(tier => {
            const hasData = tier.components.some(c => c.summary)
            if (!hasData) return null
            return (
              <div key={tier.key}>
                <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-3">
                  {tier.label}
                </h2>
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
                  {tier.components.map(comp => {
                    if (!comp.summary) return null
                    const Icon = comp.icon
                    const s = comp.summary
                    const total = s.okCount + s.warningCount + s.criticalCount + s.unknownCount
                    const worstStatus = s.criticalCount > 0 ? 'critical' :
                      s.warningCount > 0 ? 'warning' :
                      s.unknownCount > 0 ? 'unknown' : 'ok'
                    const borderColor = {
                      critical: 'border-red-300',
                      warning: 'border-yellow-300',
                      unknown: 'border-gray-300',
                      ok: 'border-green-300',
                    }[worstStatus]

                    return (
                      <Link
                        key={comp.key}
                        to={`/clusters/${id}/audit-report?component=${comp.key}`}
                        className={`bg-white border-2 ${borderColor} rounded-xl p-5 hover:shadow-md transition-shadow`}
                      >
                        <div className="flex items-center gap-3 mb-3">
                          <div className={`p-2 rounded-lg ${
                            worstStatus === 'critical' ? 'bg-red-100' :
                            worstStatus === 'warning' ? 'bg-yellow-100' :
                            worstStatus === 'ok' ? 'bg-green-100' : 'bg-gray-100'
                          }`}>
                            <Icon className={`w-5 h-5 ${
                              worstStatus === 'critical' ? 'text-red-600' :
                              worstStatus === 'warning' ? 'text-yellow-600' :
                              worstStatus === 'ok' ? 'text-green-600' : 'text-gray-500'
                            }`} />
                          </div>
                          <div>
                            <h3 className="font-semibold text-sm">{comp.label}</h3>
                            <p className="text-xs text-muted-foreground">{total} finding{total !== 1 ? 's' : ''}</p>
                          </div>
                        </div>
                        <div className="flex items-center gap-2">
                          {s.okCount > 0 && (
                            <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-green-100 text-green-700">
                              <span className="w-2 h-2 rounded-full bg-green-500" /> {s.okCount}
                            </span>
                          )}
                          {s.warningCount > 0 && (
                            <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-yellow-100 text-yellow-700">
                              <span className="w-2 h-2 rounded-full bg-yellow-500" /> {s.warningCount}
                            </span>
                          )}
                          {s.criticalCount > 0 && (
                            <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-red-100 text-red-700">
                              <span className="w-2 h-2 rounded-full bg-red-500" /> {s.criticalCount}
                            </span>
                          )}
                          {s.unknownCount > 0 && (
                            <span className="flex items-center gap-1 text-xs px-2 py-0.5 rounded-full bg-gray-100 text-gray-600">
                              <span className="w-2 h-2 rounded-full bg-gray-400" /> {s.unknownCount}
                            </span>
                          )}
                        </div>
                      </Link>
                    )
                  })}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
