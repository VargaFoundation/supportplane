import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip, LineChart, Line, XAxis, YAxis, CartesianGrid } from 'recharts'
import {
  ArrowLeft, Brain, AlertTriangle, TrendingUp, FileSearch, Settings,
  Loader2, Play, CheckCircle, XCircle, HelpCircle, Zap
} from 'lucide-react'

const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: '#ef4444', WARNING: '#eab308', INFO: '#3b82f6',
}
const SEVERITY_BG: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-800', WARNING: 'bg-yellow-100 text-yellow-800', INFO: 'bg-blue-100 text-blue-800',
}

export default function AIInsightsPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const { data: cluster } = useQuery({
    queryKey: ['cluster', id],
    queryFn: () => api.get(`/clusters/${id}`).then(r => r.data),
  })

  const { data: analysis, isLoading } = useQuery({
    queryKey: ['ai-analysis', id],
    queryFn: () => api.post(`/clusters/${id}/ai/analyze`).then(r => r.data),
    enabled: false, // Only run on demand
  })

  const analyzeMutation = useMutation({
    mutationFn: () => api.post(`/clusters/${id}/ai/analyze`).then(r => r.data),
    onSuccess: (data) => {
      queryClient.setQueryData(['ai-analysis', id], data)
    },
  })

  const data = analyzeMutation.data || analysis

  return (
    <div className="max-w-6xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(`/clusters/${id}`)}
            className="p-2 hover:bg-gray-100 rounded-md">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <div className="flex items-center gap-2">
              <Brain className="w-6 h-6 text-purple-600" />
              <h1 className="text-2xl font-bold">AI Insights</h1>
            </div>
            <p className="text-sm text-muted-foreground">
              {cluster?.name || cluster?.clusterId || 'Cluster'} — Machine Learning Analysis
            </p>
          </div>
        </div>
        <button
          onClick={() => analyzeMutation.mutate()}
          disabled={analyzeMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 bg-purple-600 text-white rounded-md text-sm hover:bg-purple-700 disabled:opacity-50"
        >
          {analyzeMutation.isPending
            ? <Loader2 className="w-4 h-4 animate-spin" />
            : <Play className="w-4 h-4" />
          }
          Run AI Analysis
        </button>
      </div>

      {!data && !analyzeMutation.isPending && (
        <div className="text-center py-20">
          <Brain className="w-16 h-16 text-purple-200 mx-auto mb-4" />
          <h2 className="text-lg font-semibold mb-2">No analysis yet</h2>
          <p className="text-muted-foreground mb-4">Click "Run AI Analysis" to detect anomalies, predict failures, analyze logs, and get tuning recommendations.</p>
        </div>
      )}

      {analyzeMutation.isPending && (
        <div className="text-center py-20">
          <Loader2 className="w-12 h-12 text-purple-400 mx-auto mb-4 animate-spin" />
          <p className="text-muted-foreground">Running anomaly detection, predictive analysis, log NLP, and auto-tuning...</p>
        </div>
      )}

      {data && (
        <div className="space-y-6">
          {/* Summary cards */}
          <div className="grid grid-cols-4 gap-4">
            <SummaryCard icon={AlertTriangle} label="Anomalies" count={data.summary?.totalAnomalies || 0}
              critical={data.summary?.criticalAnomalies || 0} color="red" />
            <SummaryCard icon={TrendingUp} label="Predictions" count={data.summary?.totalPredictions || 0}
              critical={data.summary?.urgentPredictions || 0} color="orange" />
            <SummaryCard icon={FileSearch} label="Log Patterns" count={data.summary?.logPatternCount || 0}
              critical={0} color="blue" />
            <SummaryCard icon={Settings} label="Tuning Recs" count={data.summary?.tuningRecommendationCount || 0}
              critical={0} color="purple" />
          </div>

          {data.workloadProfile && (
            <div className="bg-white border rounded-lg p-4 flex items-center gap-3">
              <Zap className="w-5 h-5 text-purple-600" />
              <span className="text-sm font-medium">Workload Profile:</span>
              <span className={`px-3 py-1 text-sm rounded-full ${
                data.workloadProfile === 'CPU_BOUND' ? 'bg-orange-100 text-orange-800' :
                data.workloadProfile === 'IO_BOUND' ? 'bg-blue-100 text-blue-800' :
                data.workloadProfile === 'MEMORY_BOUND' ? 'bg-red-100 text-red-800' :
                'bg-gray-100 text-gray-800'
              }`}>{data.workloadProfile.replace('_', ' ')}</span>
            </div>
          )}

          {/* Anomalies */}
          {data.anomalies?.length > 0 && (
            <Section title="Anomaly Detection" icon={AlertTriangle} iconColor="text-red-500">
              <div className="space-y-3">
                {data.anomalies.map((a: any, i: number) => (
                  <div key={i} className="border rounded-lg p-4 flex items-start gap-4">
                    <div className={`p-2 rounded-lg ${a.severity === 'CRITICAL' ? 'bg-red-100' : a.severity === 'WARNING' ? 'bg-yellow-100' : 'bg-blue-100'}`}>
                      <AlertTriangle className={`w-4 h-4 ${a.severity === 'CRITICAL' ? 'text-red-600' : a.severity === 'WARNING' ? 'text-yellow-600' : 'text-blue-600'}`} />
                    </div>
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-1">
                        <span className="font-medium text-sm">{a.metricName}</span>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${SEVERITY_BG[a.severity] || 'bg-gray-100'}`}>{a.severity}</span>
                        <span className="text-xs text-muted-foreground">{a.method}</span>
                      </div>
                      <p className="text-sm text-muted-foreground">{a.description}</p>
                      <div className="flex gap-4 mt-1 text-xs text-muted-foreground">
                        <span>Score: {(a.anomalyScore * 100).toFixed(0)}%</span>
                        {a.currentValue != null && <span>Value: {a.currentValue.toFixed(2)}</span>}
                        {a.expectedValue != null && <span>Expected: {a.expectedValue.toFixed(2)}</span>}
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Predictions */}
          {data.predictions?.length > 0 && (
            <Section title="Predictive Analysis" icon={TrendingUp} iconColor="text-orange-500">
              <div className="space-y-3">
                {data.predictions.map((p: any, i: number) => (
                  <div key={i} className="border rounded-lg p-4">
                    <div className="flex items-center justify-between mb-2">
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-sm">{p.metricName}</span>
                        <span className={`px-2 py-0.5 text-xs rounded-full ${SEVERITY_BG[p.severity] || 'bg-gray-100'}`}>{p.severity}</span>
                        <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100">{p.predictionType}</span>
                      </div>
                      {p.daysUntilThreshold > 0 && (
                        <span className={`text-sm font-bold ${p.daysUntilThreshold < 7 ? 'text-red-600' : p.daysUntilThreshold < 30 ? 'text-yellow-600' : 'text-green-600'}`}>
                          {p.daysUntilThreshold} days
                        </span>
                      )}
                    </div>
                    <p className="text-sm text-muted-foreground">{p.description}</p>
                    <div className="flex gap-4 mt-1 text-xs text-muted-foreground">
                      <span>Confidence: {(p.confidence * 100).toFixed(0)}%</span>
                      {p.growthRatePerDay != null && p.growthRatePerDay !== 0 && (
                        <span>Growth: {p.growthRatePerDay > 0 ? '+' : ''}{p.growthRatePerDay.toFixed(3)}/day</span>
                      )}
                      {p.exhaustionDate && <span>Estimated: {p.exhaustionDate}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Log Patterns */}
          {data.logPatterns?.length > 0 && (
            <Section title="Log Pattern Analysis (NLP)" icon={FileSearch} iconColor="text-blue-500">
              <div className="space-y-3">
                {data.logPatterns.slice(0, 15).map((lp: any, i: number) => (
                  <div key={i} className="border rounded-lg p-4">
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`px-2 py-0.5 text-xs rounded-full ${SEVERITY_BG[lp.severity] || 'bg-gray-100'}`}>{lp.severity}</span>
                      <span className="text-xs bg-slate-100 px-2 py-0.5 rounded">{lp.service}</span>
                      <span className="text-xs text-muted-foreground">{lp.occurrenceCount}x</span>
                      <span className="text-xs text-muted-foreground">Score: {(lp.severityScore * 100).toFixed(0)}%</span>
                    </div>
                    <code className="text-xs bg-gray-50 p-2 rounded block mb-2 overflow-x-auto">{lp.template}</code>
                    {lp.suggestedAction && (
                      <div className="flex items-start gap-2 mt-2 p-2 bg-green-50 rounded text-xs text-green-800">
                        <CheckCircle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
                        <span>{lp.suggestedAction}</span>
                      </div>
                    )}
                    {lp.correlatedClusters?.length > 0 && (
                      <div className="text-xs text-muted-foreground mt-1">
                        Correlated with: {lp.correlatedClusters.join(', ')}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </Section>
          )}

          {/* Tuning Recommendations */}
          {data.tuningRecommendations?.length > 0 && (
            <Section title="Auto-Tuning Recommendations" icon={Settings} iconColor="text-purple-500">
              <div className="overflow-x-auto">
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2 px-3 font-medium">Parameter</th>
                      <th className="py-2 px-3 font-medium">Component</th>
                      <th className="py-2 px-3 font-medium">Current</th>
                      <th className="py-2 px-3 font-medium">Suggested</th>
                      <th className="py-2 px-3 font-medium">Confidence</th>
                      <th className="py-2 px-3 font-medium">Impact</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.tuningRecommendations.map((tr: any, i: number) => (
                      <tr key={i} className="border-b hover:bg-gray-50">
                        <td className="py-2 px-3">
                          <code className="text-xs bg-gray-100 px-1 rounded">{tr.parameter}</code>
                        </td>
                        <td className="py-2 px-3">
                          <span className="px-2 py-0.5 text-xs rounded-full bg-slate-100">{tr.component}</span>
                        </td>
                        <td className="py-2 px-3 text-muted-foreground">{tr.currentValue}</td>
                        <td className="py-2 px-3 font-medium text-purple-700">{tr.suggestedValue}</td>
                        <td className="py-2 px-3">
                          <div className="flex items-center gap-2">
                            <div className="w-16 h-1.5 bg-gray-200 rounded-full">
                              <div className="h-full bg-purple-500 rounded-full"
                                style={{ width: `${tr.confidence * 100}%` }} />
                            </div>
                            <span className="text-xs">{(tr.confidence * 100).toFixed(0)}%</span>
                          </div>
                        </td>
                        <td className="py-2 px-3 text-xs text-muted-foreground">{tr.expectedImpact}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {data.tuningRecommendations[0]?.justification && (
                <div className="mt-3 p-3 bg-purple-50 rounded text-xs text-purple-800">
                  <strong>Justification:</strong> {data.tuningRecommendations[0].justification}
                </div>
              )}
            </Section>
          )}
        </div>
      )}
    </div>
  )
}

function SummaryCard({ icon: Icon, label, count, critical, color }: {
  icon: any; label: string; count: number; critical: number; color: string
}) {
  const colorMap: Record<string, string> = {
    red: 'bg-red-50 text-red-600', orange: 'bg-orange-50 text-orange-600',
    blue: 'bg-blue-50 text-blue-600', purple: 'bg-purple-50 text-purple-600',
  }
  return (
    <div className="bg-white border rounded-lg p-4">
      <div className="flex items-center gap-3 mb-2">
        <div className={`p-2 rounded-lg ${colorMap[color]}`}>
          <Icon className="w-4 h-4" />
        </div>
        <span className="text-sm font-medium">{label}</span>
      </div>
      <div className="text-2xl font-bold">{count}</div>
      {critical > 0 && (
        <div className="text-xs text-red-600 mt-1">{critical} critical</div>
      )}
    </div>
  )
}

function Section({ title, icon: Icon, iconColor, children }: {
  title: string; icon: any; iconColor: string; children: React.ReactNode
}) {
  return (
    <div className="bg-white border rounded-lg p-6">
      <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
        <Icon className={`w-5 h-5 ${iconColor}`} />
        {title}
      </h2>
      {children}
    </div>
  )
}
