import { useParams, useNavigate, useSearchParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { PieChart, Pie, Cell, ResponsiveContainer, Tooltip } from 'recharts'
import {
  CheckCircle, AlertTriangle, XCircle, HelpCircle,
  ArrowLeft, Printer, CheckCircle2, Send
} from 'lucide-react'

const STATUS_CONFIG: Record<string, {
  icon: React.ElementType
  color: string
  bgColor: string
  label: string
}> = {
  OK: { icon: CheckCircle, color: 'text-green-600', bgColor: 'bg-green-100', label: 'OK' },
  WARNING: { icon: AlertTriangle, color: 'text-yellow-600', bgColor: 'bg-yellow-100', label: 'Warning' },
  CRITICAL: { icon: XCircle, color: 'text-red-600', bgColor: 'bg-red-100', label: 'Critical' },
  UNKNOWN: { icon: HelpCircle, color: 'text-gray-500', bgColor: 'bg-gray-100', label: 'Unknown' },
}

const PIE_COLORS: Record<string, string> = {
  OK: '#22c55e',
  WARNING: '#eab308',
  CRITICAL: '#ef4444',
  UNKNOWN: '#9ca3af',
}

const WORKFLOW_COLORS: Record<string, string> = {
  DRAFT: 'bg-gray-100 text-gray-800',
  VALIDATED: 'bg-blue-100 text-blue-800',
  DELIVERED: 'bg-green-100 text-green-800',
}

const RISK_COLORS: Record<string, string> = {
  HIGH: 'bg-red-100 text-red-800',
  MEDIUM: 'bg-yellow-100 text-yellow-800',
  LOW: 'bg-green-100 text-green-800',
}

const LIKELIHOOD_COLORS: Record<string, string> = {
  HIGH: 'text-red-600',
  MEDIUM: 'text-yellow-600',
  LOW: 'text-green-600',
}

export default function AuditReportPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const componentFilter = searchParams.get('component')

  const { data: report, isLoading } = useQuery({
    queryKey: ['audit-report', id],
    queryFn: () => api.get(`/clusters/${id}/audit-report`).then((r) => r.data),
  })

  const validateMutation = useMutation({
    mutationFn: (recId: number) => api.put(`/recommendations/${recId}/validate`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['audit-report', id] }),
  })

  const deliverMutation = useMutation({
    mutationFn: (recId: number) => api.put(`/recommendations/${recId}/deliver`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['audit-report', id] }),
  })

  if (isLoading) {
    return <div className="text-muted-foreground text-center py-12">Loading audit report...</div>
  }

  if (!report || (!report.categories?.length)) {
    return (
      <div className="text-center py-16">
        <HelpCircle className="w-12 h-12 text-gray-300 mx-auto mb-4" />
        <p className="text-muted-foreground mb-2">No audit report available</p>
        <p className="text-sm text-muted-foreground">Run an audit from the architecture view first</p>
        <button onClick={() => navigate(`/clusters/${id}/audit`)}
          className="mt-4 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
          Go to Architecture View
        </button>
      </div>
    )
  }

  const summary = report.summary || {}
  const pieData = ['OK', 'WARNING', 'CRITICAL', 'UNKNOWN']
    .filter(k => (summary[k] || 0) > 0)
    .map(k => ({ name: k, value: summary[k] }))

  // Filter by component if specified
  const filterFindings = (findings: any[]) => {
    if (!componentFilter) return findings
    return findings.filter((f: any) => f.component === componentFilter)
  }

  // Build table of contents
  const toc: { id: string; label: string; level: number }[] = []
  report.categories?.forEach((cat: any) => {
    toc.push({ id: `cat-${cat.name}`, label: cat.name, level: 1 })
    cat.subcategories?.forEach((sub: any) => {
      toc.push({ id: `sub-${cat.name}-${sub.name}`, label: sub.name, level: 2 })
    })
  })

  return (
    <div className="max-w-5xl mx-auto">
      {/* Header - hidden in print */}
      <div className="flex items-center justify-between mb-6 print:hidden">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(`/clusters/${id}/audit`)}
            className="p-2 hover:bg-gray-100 rounded-md">
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold">Audit Report</h1>
            <p className="text-sm text-muted-foreground">
              {report.clusterName} — {new Date(report.generatedAt).toLocaleDateString()}
              {componentFilter && <span className="ml-2 px-2 py-0.5 bg-slate-100 text-slate-700 rounded text-xs">Filtered: {componentFilter}</span>}
            </p>
          </div>
        </div>
        <button onClick={() => window.print()}
          className="flex items-center gap-2 px-4 py-2 border rounded-md text-sm hover:bg-gray-50">
          <Printer className="w-4 h-4" /> Print / PDF
        </button>
      </div>

      {/* Print header */}
      <div className="hidden print:block mb-8">
        <h1 className="text-3xl font-bold mb-2">Performances and Security Report</h1>
        <p className="text-lg text-gray-600">Cluster: {report.clusterName}</p>
        <p className="text-sm text-gray-500">Generated: {new Date(report.generatedAt).toLocaleString()}</p>
      </div>

      {/* Summary section */}
      <div className="bg-white border rounded-lg p-6 mb-6 print:border-0 print:p-0 print:mb-8">
        <h2 className="text-lg font-semibold mb-4">Summary</h2>
        <div className="flex items-center gap-8">
          <div className="w-40 h-40">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie data={pieData} dataKey="value" cx="50%" cy="50%" outerRadius={60} innerRadius={30}>
                  {pieData.map((entry) => (
                    <Cell key={entry.name} fill={PIE_COLORS[entry.name]} />
                  ))}
                </Pie>
                <Tooltip />
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex-1 grid grid-cols-4 gap-4">
            {Object.entries(STATUS_CONFIG).map(([key, cfg]) => {
              const count = summary[key] || 0
              const Icon = cfg.icon
              return (
                <div key={key} className={`p-4 rounded-lg ${cfg.bgColor}`}>
                  <div className="flex items-center gap-2 mb-1">
                    <Icon className={`w-5 h-5 ${cfg.color}`} />
                    <span className="font-semibold text-lg">{count}</span>
                  </div>
                  <p className="text-xs text-gray-600">{cfg.label}</p>
                </div>
              )
            })}
          </div>
        </div>
        <div className="mt-3 text-sm text-muted-foreground">
          Total: {summary.total || 0} findings
        </div>
      </div>

      {/* Table of contents */}
      <div className="bg-white border rounded-lg p-6 mb-6 print:break-after-page">
        <h2 className="text-lg font-semibold mb-3">Table of Contents</h2>
        <nav className="space-y-1">
          {toc.map((item) => (
            <a key={item.id} href={`#${item.id}`}
              className={`block text-sm hover:text-primary ${
                item.level === 1 ? 'font-medium' : 'pl-4 text-muted-foreground'
              }`}>
              {item.label}
            </a>
          ))}
        </nav>
      </div>

      {/* Findings by category */}
      {report.categories?.map((cat: any) => {
        const catFindings = filterFindings(cat.findings || [])
        const catSubcategories = (cat.subcategories || []).map((sub: any) => ({
          ...sub,
          findings: filterFindings(sub.findings || []),
        })).filter((sub: any) => sub.findings.length > 0)

        if (catFindings.length === 0 && catSubcategories.length === 0) return null

        return (
          <div key={cat.name} className="mb-8 print:break-inside-avoid-page">
            <h2 id={`cat-${cat.name}`} className="text-xl font-bold mb-4 border-b pb-2">
              {cat.name}
            </h2>

            {/* Direct findings (no subcategory) */}
            {catFindings.map((finding: any) => (
              <FindingCard key={finding.id} finding={finding}
                onValidate={() => validateMutation.mutate(finding.id)}
                onDeliver={() => deliverMutation.mutate(finding.id)} />
            ))}

            {/* Subcategories */}
            {catSubcategories.map((sub: any) => (
              <div key={sub.name} className="mb-6">
                <h3 id={`sub-${cat.name}-${sub.name}`}
                  className="text-lg font-semibold mb-3 text-gray-700">
                  {sub.name}
                </h3>
                {sub.findings.map((finding: any) => (
                  <FindingCard key={finding.id} finding={finding}
                    onValidate={() => validateMutation.mutate(finding.id)}
                    onDeliver={() => deliverMutation.mutate(finding.id)} />
                ))}
              </div>
            ))}
          </div>
        )
      })}

      {/* Legend */}
      <div className="bg-white border rounded-lg p-6 mt-8 print:mt-4">
        <h2 className="text-lg font-semibold mb-3">Legend</h2>
        <div className="space-y-2 text-sm">
          {Object.entries(STATUS_CONFIG).map(([key, cfg]) => {
            const Icon = cfg.icon
            return (
              <div key={key} className="flex items-center gap-3">
                <Icon className={`w-5 h-5 ${cfg.color}`} />
                <span>
                  {key === 'OK' && 'The current state is good for this control, or some minor corrective actions may be needed.'}
                  {key === 'WARNING' && 'The current state is not fully compliant, a plan for corrective measures should be developed within a reasonable period.'}
                  {key === 'CRITICAL' && 'The current state is critical, a plan for corrective measures should be developed as soon as possible.'}
                  {key === 'UNKNOWN' && 'During the audit process, we were unable to determine the actual state on this control.'}
                </span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function FindingCard({ finding, onValidate, onDeliver }: {
  finding: any
  onValidate: () => void
  onDeliver: () => void
}) {
  const statusCfg = STATUS_CONFIG[finding.findingStatus] || STATUS_CONFIG.UNKNOWN
  const StatusIcon = statusCfg.icon
  const threats = parseJsonArray(finding.threat)
  const vulnerabilities = parseJsonArray(finding.vulnerability)
  const assets = parseJsonArray(finding.asset)
  const recommendations = parseJsonArray(finding.recommendationsText)

  return (
    <div className="bg-white border rounded-lg mb-4 print:break-inside-avoid print:border-gray-300">
      {/* Finding title bar */}
      <div className="flex items-center justify-between px-4 py-3 border-b bg-gray-50 rounded-t-lg">
        <div className="flex items-center gap-3">
          <StatusIcon className={`w-5 h-5 ${statusCfg.color}`} />
          <h4 className="font-semibold text-sm">{finding.title}</h4>
          {finding.ruleCode && (
            <code className="text-xs bg-gray-200 px-1.5 py-0.5 rounded">{finding.ruleCode}</code>
          )}
        </div>
        <div className="flex items-center gap-2 print:hidden">
          <span className={`px-2 py-0.5 text-xs rounded-full ${WORKFLOW_COLORS[finding.status] || 'bg-gray-100'}`}>
            {finding.status}
          </span>
          {finding.status === 'DRAFT' && (
            <button onClick={onValidate}
              className="flex items-center gap-1 px-2 py-1 text-xs bg-blue-50 text-blue-700 rounded hover:bg-blue-100">
              <CheckCircle2 className="w-3 h-3" /> Validate
            </button>
          )}
          {finding.status === 'VALIDATED' && (
            <button onClick={onDeliver}
              className="flex items-center gap-1 px-2 py-1 text-xs bg-green-50 text-green-700 rounded hover:bg-green-100">
              <Send className="w-3 h-3" /> Deliver
            </button>
          )}
        </div>
      </div>

      {/* Three-column layout like audit.asciidoc */}
      <div className="grid grid-cols-[60px_1fr_1fr] gap-0 divide-x">
        {/* Column 1: Status icon */}
        <div className="flex items-center justify-center p-4">
          <StatusIcon className={`w-8 h-8 ${statusCfg.color}`} />
        </div>

        {/* Column 2: Threat, vulnerability, asset, impact, likelihood, risk */}
        <div className="p-4 space-y-3 text-sm">
          {threats.length > 0 && (
            <div>
              <p className="font-semibold text-xs text-gray-500 uppercase">Threat</p>
              <ul className="list-disc list-inside">
                {threats.map((t, i) => <li key={i}>{t}</li>)}
              </ul>
            </div>
          )}
          {vulnerabilities.length > 0 && (
            <div>
              <p className="font-semibold text-xs text-gray-500 uppercase">Vulnerability</p>
              <ul className="list-disc list-inside">
                {vulnerabilities.map((v, i) => <li key={i}>{v}</li>)}
              </ul>
            </div>
          )}
          {assets.length > 0 && (
            <div>
              <p className="font-semibold text-xs text-gray-500 uppercase">Asset</p>
              <ul className="list-disc list-inside">
                {assets.map((a, i) => <li key={i}>{a}</li>)}
              </ul>
            </div>
          )}
          {finding.impact && (
            <div>
              <p className="font-semibold text-xs text-gray-500 uppercase">Impact</p>
              <p>{finding.impact}</p>
            </div>
          )}
          <div className="flex items-center gap-4">
            {finding.likelihood && (
              <div>
                <span className="font-semibold text-xs text-gray-500 uppercase">Likelihood </span>
                <span className={`font-medium ${LIKELIHOOD_COLORS[finding.likelihood] || ''}`}>
                  {finding.likelihood}
                </span>
              </div>
            )}
            {finding.risk && (
              <div>
                <span className="font-semibold text-xs text-gray-500 uppercase">Risk </span>
                <span className={`px-2 py-0.5 text-xs rounded-full font-medium ${RISK_COLORS[finding.risk] || 'bg-gray-100'}`}>
                  {finding.risk}
                </span>
              </div>
            )}
          </div>
        </div>

        {/* Column 3: Recommendations */}
        <div className="p-4 text-sm">
          <p className="font-semibold text-xs text-gray-500 uppercase mb-2">Recommendations</p>
          {recommendations.length > 0 ? (
            <ol className="list-decimal list-inside space-y-1">
              {recommendations.map((r, i) => <li key={i}>{r}</li>)}
            </ol>
          ) : (
            <p className="text-muted-foreground text-xs">No specific recommendations.</p>
          )}
        </div>
      </div>
    </div>
  )
}

function parseJsonArray(json: string | null): string[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return Array.isArray(arr) ? arr : [json]
  } catch {
    return [json]
  }
}
