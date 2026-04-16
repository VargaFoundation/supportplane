import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import api from '@/lib/api'
import { Settings, Plus, ChevronDown, ChevronRight, ToggleLeft, ToggleRight, Trash2, Edit2, X } from 'lucide-react'

const CATEGORIES = ['Performance', 'Security', 'Architecture']
const SUBCATEGORIES: Record<string, string[]> = {
  Performance: ['HDFS', 'Yarn'],
  Security: ['Data Protection', 'Authentication', 'Authorization', 'Access', 'Auditing'],
  Architecture: ['Network', 'High Availability', 'OS'],
}
const COMPONENTS = ['HDFS', 'Yarn', 'Hive', 'HBase', 'Kerberos', 'Knox', 'Ranger', 'Ambari', 'Network', 'OS', 'Platform']
const SEVERITIES = ['INFO', 'WARNING', 'CRITICAL']
const LIKELIHOODS = ['LOW', 'MEDIUM', 'HIGH']

export default function RulesPage() {
  const queryClient = useQueryClient()
  const [showCreate, setShowCreate] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [categoryFilter, setCategoryFilter] = useState('ALL')

  // Form state
  const [code, setCode] = useState('')
  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [category, setCategory] = useState('Security')
  const [subcategory, setSubcategory] = useState('')
  const [component, setComponent] = useState('HDFS')
  const [threat, setThreat] = useState('')
  const [vulnerability, setVulnerability] = useState('')
  const [asset, setAsset] = useState('')
  const [impact, setImpact] = useState('')
  const [defaultLikelihood, setDefaultLikelihood] = useState('MEDIUM')
  const [defaultSeverity, setDefaultSeverity] = useState('WARNING')
  const [recommendationsText, setRecommendationsText] = useState('')
  const [conditionType, setConditionType] = useState('metadata_check')
  const [conditionPath, setConditionPath] = useState('')
  const [conditionOperator, setConditionOperator] = useState('equals')
  const [conditionExpected, setConditionExpected] = useState('')

  const { data: rules, isLoading } = useQuery({
    queryKey: ['recommendation-rules', categoryFilter],
    queryFn: () => api.get('/recommendation-rules', {
      params: categoryFilter !== 'ALL' ? { category: categoryFilter } : {},
    }).then((r) => r.data),
  })

  const createMutation = useMutation({
    mutationFn: (data: any) => api.post('/recommendation-rules', data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recommendation-rules'] })
      resetForm()
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) =>
      api.put(`/recommendation-rules/${id}`, data).then((r) => r.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recommendation-rules'] })
      setEditingId(null)
      resetForm()
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/recommendation-rules/${id}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recommendation-rules'] }),
  })

  const toggleMutation = useMutation({
    mutationFn: (id: number) => api.put(`/recommendation-rules/${id}/toggle`).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['recommendation-rules'] }),
  })

  const resetForm = () => {
    setShowCreate(false)
    setCode(''); setTitle(''); setDescription('')
    setCategory('Security'); setSubcategory(''); setComponent('HDFS')
    setThreat(''); setVulnerability(''); setAsset('')
    setImpact(''); setDefaultLikelihood('MEDIUM'); setDefaultSeverity('WARNING')
    setRecommendationsText('')
    setConditionType('metadata_check'); setConditionPath(''); setConditionOperator('equals'); setConditionExpected('')
  }

  const buildCondition = () => {
    if (conditionType === 'always') return { type: 'always' }
    const cond: any = { type: conditionType, path: conditionPath }
    if (conditionType === 'metadata_check') {
      cond.operator = conditionOperator
      // Try to parse as boolean or number
      let expected: any = conditionExpected
      if (expected === 'true') expected = true
      else if (expected === 'false') expected = false
      else if (!isNaN(Number(expected)) && expected !== '') expected = Number(expected)
      cond.expected = expected
    }
    return cond
  }

  const linesToJson = (text: string) => {
    const lines = text.split('\n').map(l => l.trim()).filter(Boolean)
    return JSON.stringify(lines)
  }

  const jsonToLines = (json: string | null) => {
    if (!json) return ''
    try {
      const arr = JSON.parse(json)
      return Array.isArray(arr) ? arr.join('\n') : json
    } catch { return json }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const data = {
      code, title, description, category, subcategory, component,
      threat: linesToJson(threat),
      vulnerability: linesToJson(vulnerability),
      asset: linesToJson(asset),
      impact,
      defaultLikelihood, defaultSeverity,
      recommendationsText: linesToJson(recommendationsText),
      condition: buildCondition(),
    }
    if (editingId) {
      updateMutation.mutate({ id: editingId, data })
    } else {
      createMutation.mutate(data)
    }
  }

  const startEdit = (rule: any) => {
    setEditingId(rule.id)
    setShowCreate(true)
    setCode(rule.code); setTitle(rule.title); setDescription(rule.description || '')
    setCategory(rule.category); setSubcategory(rule.subcategory || ''); setComponent(rule.component)
    setThreat(jsonToLines(rule.threat)); setVulnerability(jsonToLines(rule.vulnerability))
    setAsset(jsonToLines(rule.asset)); setImpact(rule.impact || '')
    setDefaultLikelihood(rule.defaultLikelihood || 'MEDIUM')
    setDefaultSeverity(rule.defaultSeverity || 'WARNING')
    setRecommendationsText(jsonToLines(rule.recommendationsText))
    if (rule.condition) {
      setConditionType(rule.condition.type || 'metadata_check')
      setConditionPath(rule.condition.path || '')
      setConditionOperator(rule.condition.operator || 'equals')
      setConditionExpected(rule.condition.expected?.toString() || '')
    }
  }

  const severityColor: Record<string, string> = {
    CRITICAL: 'bg-red-100 text-red-800',
    WARNING: 'bg-yellow-100 text-yellow-800',
    INFO: 'bg-blue-100 text-blue-800',
  }

  const categoryColor: Record<string, string> = {
    Performance: 'bg-purple-100 text-purple-800',
    Security: 'bg-red-100 text-red-800',
    Architecture: 'bg-indigo-100 text-indigo-800',
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold">Audit Rules</h1>
        <button
          onClick={() => { resetForm(); setShowCreate(true) }}
          className="flex items-center gap-2 px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm hover:bg-primary/90"
        >
          <Plus className="w-4 h-4" /> New Rule
        </button>
      </div>

      {/* Create/Edit Form */}
      {showCreate && (
        <div className="bg-white border rounded-lg p-6 mb-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold">{editingId ? 'Edit Rule' : 'New Rule'}</h2>
            <button onClick={() => { resetForm(); setEditingId(null) }} className="text-gray-400 hover:text-gray-600">
              <X className="w-5 h-5" />
            </button>
          </div>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Code</label>
                <input type="text" value={code} onChange={(e) => setCode(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" placeholder="SEC-AUTH-KERBEROS" required />
              </div>
              <div className="col-span-2">
                <label className="block text-sm font-medium mb-1">Title</label>
                <input type="text" value={title} onChange={(e) => setTitle(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" required />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Description</label>
              <textarea value={description} onChange={(e) => setDescription(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" rows={2} />
            </div>
            <div className="grid grid-cols-4 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Category</label>
                <select value={category} onChange={(e) => { setCategory(e.target.value); setSubcategory('') }}
                  className="w-full px-3 py-2 border rounded-md text-sm">
                  {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Subcategory</label>
                <select value={subcategory} onChange={(e) => setSubcategory(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm">
                  <option value="">None</option>
                  {(SUBCATEGORIES[category] || []).map(s => <option key={s} value={s}>{s}</option>)}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Component</label>
                <select value={component} onChange={(e) => setComponent(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm">
                  {COMPONENTS.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-sm font-medium mb-1">Severity</label>
                  <select value={defaultSeverity} onChange={(e) => setDefaultSeverity(e.target.value)}
                    className="w-full px-3 py-2 border rounded-md text-sm">
                    {SEVERITIES.map(s => <option key={s} value={s}>{s}</option>)}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1">Likelihood</label>
                  <select value={defaultLikelihood} onChange={(e) => setDefaultLikelihood(e.target.value)}
                    className="w-full px-3 py-2 border rounded-md text-sm">
                    {LIKELIHOODS.map(l => <option key={l} value={l}>{l}</option>)}
                  </select>
                </div>
              </div>
            </div>
            <div className="grid grid-cols-3 gap-4">
              <div>
                <label className="block text-sm font-medium mb-1">Threats (one per line)</label>
                <textarea value={threat} onChange={(e) => setThreat(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" rows={3} />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Vulnerabilities (one per line)</label>
                <textarea value={vulnerability} onChange={(e) => setVulnerability(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" rows={3} />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1">Assets (one per line)</label>
                <textarea value={asset} onChange={(e) => setAsset(e.target.value)}
                  className="w-full px-3 py-2 border rounded-md text-sm" rows={3} />
              </div>
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Impact</label>
              <textarea value={impact} onChange={(e) => setImpact(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" rows={2} />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1">Recommendations (one per line)</label>
              <textarea value={recommendationsText} onChange={(e) => setRecommendationsText(e.target.value)}
                className="w-full px-3 py-2 border rounded-md text-sm" rows={3} />
            </div>
            {/* Condition */}
            <div className="border rounded-md p-4 bg-gray-50">
              <label className="block text-sm font-semibold mb-2">Evaluation Condition</label>
              <div className="grid grid-cols-4 gap-4">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Type</label>
                  <select value={conditionType} onChange={(e) => setConditionType(e.target.value)}
                    className="w-full px-3 py-2 border rounded-md text-sm">
                    <option value="metadata_check">Metadata Check</option>
                    <option value="metadata_absent">Metadata Absent</option>
                    <option value="always">Always (Manual)</option>
                  </select>
                </div>
                {conditionType !== 'always' && (
                  <div>
                    <label className="block text-xs text-gray-500 mb-1">Path</label>
                    <input type="text" value={conditionPath} onChange={(e) => setConditionPath(e.target.value)}
                      className="w-full px-3 py-2 border rounded-md text-sm" placeholder="security.kerberos.enabled" />
                  </div>
                )}
                {conditionType === 'metadata_check' && (
                  <>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Operator</label>
                      <select value={conditionOperator} onChange={(e) => setConditionOperator(e.target.value)}
                        className="w-full px-3 py-2 border rounded-md text-sm">
                        <option value="equals">Equals</option>
                        <option value="not_equals">Not Equals</option>
                        <option value="greater_than">Greater Than</option>
                        <option value="less_than">Less Than</option>
                        <option value="contains">Contains</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-xs text-gray-500 mb-1">Expected Value</label>
                      <input type="text" value={conditionExpected} onChange={(e) => setConditionExpected(e.target.value)}
                        className="w-full px-3 py-2 border rounded-md text-sm" placeholder="true" />
                    </div>
                  </>
                )}
              </div>
            </div>
            <div className="flex gap-2">
              <button type="submit" className="px-4 py-2 bg-primary text-primary-foreground rounded-md text-sm">
                {editingId ? 'Update Rule' : 'Create Rule'}
              </button>
              <button type="button" onClick={() => { resetForm(); setEditingId(null) }}
                className="px-4 py-2 border rounded-md text-sm">Cancel</button>
            </div>
          </form>
        </div>
      )}

      {/* Category filter */}
      <div className="flex gap-1 mb-4">
        {['ALL', ...CATEGORIES].map((c) => (
          <button key={c} onClick={() => setCategoryFilter(c)}
            className={`px-3 py-1 text-xs rounded-full ${
              categoryFilter === c ? 'bg-primary text-primary-foreground' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}>
            {c}
          </button>
        ))}
      </div>

      {/* Rules list */}
      {isLoading ? (
        <div className="text-muted-foreground">Loading rules...</div>
      ) : (
        <div className="space-y-3">
          {rules?.map((rule: any) => (
            <div key={rule.id} className="bg-white border rounded-lg">
              <div className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-3 flex-1 cursor-pointer"
                  onClick={() => setExpandedId(expandedId === rule.id ? null : rule.id)}>
                  {expandedId === rule.id
                    ? <ChevronDown className="w-4 h-4 text-gray-400" />
                    : <ChevronRight className="w-4 h-4 text-gray-400" />
                  }
                  <div className="flex items-center gap-2">
                    <Settings className="w-4 h-4 text-gray-400" />
                    <code className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">{rule.code}</code>
                    <span className="font-medium text-sm">{rule.title}</span>
                  </div>
                  <span className={`px-2 py-0.5 text-xs rounded-full ${categoryColor[rule.category] || 'bg-gray-100'}`}>
                    {rule.category}
                  </span>
                  {rule.subcategory && (
                    <span className="px-2 py-0.5 text-xs rounded-full bg-gray-100 text-gray-600">
                      {rule.subcategory}
                    </span>
                  )}
                  <span className="px-2 py-0.5 text-xs rounded-full bg-slate-100 text-slate-700">
                    {rule.component}
                  </span>
                  <span className={`px-2 py-0.5 text-xs rounded-full ${severityColor[rule.defaultSeverity] || 'bg-gray-100'}`}>
                    {rule.defaultSeverity}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <button onClick={() => toggleMutation.mutate(rule.id)}
                    className="p-1 hover:bg-gray-100 rounded" title={rule.enabled ? 'Disable' : 'Enable'}>
                    {rule.enabled
                      ? <ToggleRight className="w-5 h-5 text-green-600" />
                      : <ToggleLeft className="w-5 h-5 text-gray-400" />
                    }
                  </button>
                  <button onClick={() => startEdit(rule)}
                    className="p-1 hover:bg-gray-100 rounded" title="Edit">
                    <Edit2 className="w-4 h-4 text-gray-500" />
                  </button>
                  <button onClick={() => { if (confirm('Delete this rule?')) deleteMutation.mutate(rule.id) }}
                    className="p-1 hover:bg-gray-100 rounded" title="Delete">
                    <Trash2 className="w-4 h-4 text-red-500" />
                  </button>
                </div>
              </div>
              {/* Expanded detail */}
              {expandedId === rule.id && (
                <div className="border-t px-4 py-3 bg-gray-50 text-sm space-y-2">
                  {rule.description && <p className="text-gray-600">{rule.description}</p>}
                  <div className="grid grid-cols-3 gap-4">
                    <div>
                      <p className="font-medium text-xs text-gray-500 mb-1">Threats</p>
                      <ul className="list-disc list-inside text-xs">
                        {parseJsonArray(rule.threat).map((t: string, i: number) => <li key={i}>{t}</li>)}
                      </ul>
                    </div>
                    <div>
                      <p className="font-medium text-xs text-gray-500 mb-1">Vulnerabilities</p>
                      <ul className="list-disc list-inside text-xs">
                        {parseJsonArray(rule.vulnerability).map((v: string, i: number) => <li key={i}>{v}</li>)}
                      </ul>
                    </div>
                    <div>
                      <p className="font-medium text-xs text-gray-500 mb-1">Assets</p>
                      <ul className="list-disc list-inside text-xs">
                        {parseJsonArray(rule.asset).map((a: string, i: number) => <li key={i}>{a}</li>)}
                      </ul>
                    </div>
                  </div>
                  {rule.impact && (
                    <div>
                      <p className="font-medium text-xs text-gray-500 mb-1">Impact</p>
                      <p className="text-xs">{rule.impact}</p>
                    </div>
                  )}
                  <div className="flex gap-4">
                    <span className="text-xs"><strong>Likelihood:</strong> {rule.defaultLikelihood}</span>
                    <span className="text-xs"><strong>Risk:</strong> calculated at evaluation</span>
                  </div>
                  <div>
                    <p className="font-medium text-xs text-gray-500 mb-1">Recommendations</p>
                    <ol className="list-decimal list-inside text-xs">
                      {parseJsonArray(rule.recommendationsText).map((r: string, i: number) => <li key={i}>{r}</li>)}
                    </ol>
                  </div>
                  {rule.condition && (
                    <div>
                      <p className="font-medium text-xs text-gray-500 mb-1">Condition</p>
                      <code className="text-xs bg-white px-2 py-1 rounded border block">
                        {JSON.stringify(rule.condition)}
                      </code>
                    </div>
                  )}
                </div>
              )}
            </div>
          ))}
          {(!rules || rules.length === 0) && (
            <div className="text-center py-12 text-muted-foreground">No rules found.</div>
          )}
        </div>
      )}
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
