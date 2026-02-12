import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import api from '@/lib/api'
import { Package, FileText, Cpu, HardDrive, Shield, Key, Activity } from 'lucide-react'
import { useState } from 'react'

type TabKey = 'overview' | 'topology' | 'metrics' | 'jmx' | 'kerberos' | 'ssl' | 'kernel' | 'drift' | 'analysis'

export default function BundleViewerPage() {
  const { id } = useParams()
  const [activeTab, setActiveTab] = useState<TabKey>('overview')

  const { data: bundle } = useQuery({
    queryKey: ['bundle', id],
    queryFn: () => api.get(`/bundles/${id}`).then((r) => r.data),
  })

  const { data: contents } = useQuery({
    queryKey: ['bundle-contents', id],
    queryFn: () => api.get(`/bundles/${id}/contents`).then((r) => r.data),
    enabled: !!bundle,
  })

  if (!bundle) return <div className="text-muted-foreground">Loading...</div>

  const tabs: { key: TabKey; label: string; icon: any }[] = [
    { key: 'overview', label: 'Overview', icon: Package },
    { key: 'topology', label: 'Topology', icon: FileText },
    { key: 'metrics', label: 'System Metrics', icon: Cpu },
    { key: 'jmx', label: 'JMX Metrics', icon: Activity },
    { key: 'kerberos', label: 'Kerberos', icon: Key },
    { key: 'ssl', label: 'SSL Certs', icon: Shield },
    { key: 'kernel', label: 'Kernel', icon: HardDrive },
    { key: 'drift', label: 'Config Drift', icon: FileText },
    { key: 'analysis', label: 'Analysis', icon: FileText },
  ]

  return (
    <div>
      <div className="flex items-center gap-4 mb-6">
        <div className="p-3 bg-primary/10 rounded-lg">
          <Package className="w-6 h-6 text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold">{bundle.filename}</h1>
          <p className="text-sm text-muted-foreground">
            Received {new Date(bundle.receivedAt).toLocaleString()} — {(bundle.sizeBytes / 1024 / 1024).toFixed(2)} MB
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b mb-6 overflow-x-auto">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`flex items-center gap-2 px-4 py-2 text-sm whitespace-nowrap border-b-2 -mb-px transition-colors ${
              activeTab === tab.key
                ? 'border-primary text-primary font-medium'
                : 'border-transparent text-muted-foreground hover:text-foreground'
            }`}
          >
            <tab.icon className="w-4 h-4" />
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      <div className="bg-white border rounded-lg p-6">
        {activeTab === 'overview' && (
          <div className="space-y-4">
            <h2 className="text-lg font-semibold">Bundle Overview</h2>
            <dl className="grid grid-cols-2 gap-4">
              <div>
                <dt className="text-xs text-muted-foreground">Cluster ID</dt>
                <dd className="text-sm font-mono">{bundle.clusterId}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Bundle Level</dt>
                <dd className="text-sm">{bundle.metadata?.level || '-'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">ODPSC Version</dt>
                <dd className="text-sm">{bundle.metadata?.odpscVersion || '-'}</dd>
              </div>
              <div>
                <dt className="text-xs text-muted-foreground">Collection Time</dt>
                <dd className="text-sm">{bundle.metadata?.collectionTime || '-'}</dd>
              </div>
            </dl>
            {contents?.files && (
              <div>
                <h3 className="text-sm font-semibold mb-2">Files in Bundle</h3>
                <div className="bg-gray-50 rounded-md p-3 max-h-64 overflow-y-auto">
                  {contents.files.map((f: string) => (
                    <div key={f} className="text-xs font-mono py-0.5">{f}</div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}

        {activeTab === 'topology' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Cluster Topology</h2>
            {contents?.topology ? (
              <div className="space-y-3">
                {Object.entries(contents.topology).map(([host, services]: [string, any]) => (
                  <div key={host} className="border rounded-md p-3">
                    <h3 className="text-sm font-medium font-mono">{host}</h3>
                    <div className="flex flex-wrap gap-1 mt-2">
                      {(Array.isArray(services) ? services : []).map((s: string) => (
                        <span key={s} className="px-2 py-0.5 bg-blue-50 text-blue-700 text-xs rounded">
                          {s}
                        </span>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No topology data available.</p>
            )}
          </div>
        )}

        {activeTab === 'metrics' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">System Metrics</h2>
            {contents?.granularMetrics ? (
              <pre className="bg-gray-50 rounded-md p-4 text-xs overflow-auto max-h-96">
                {JSON.stringify(contents.granularMetrics, null, 2)}
              </pre>
            ) : (
              <p className="text-sm text-muted-foreground">No granular metrics available (L2+ bundles only).</p>
            )}
          </div>
        )}

        {activeTab === 'jmx' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">JMX Metrics</h2>
            {contents?.jmxMetrics ? (
              <div className="space-y-4">
                {Object.entries(contents.jmxMetrics).map(([component, data]: [string, any]) => (
                  <div key={component} className="border rounded-md p-4">
                    <h3 className="text-sm font-semibold mb-2 capitalize">{component.replace('_', ' ')}</h3>
                    <pre className="bg-gray-50 rounded p-3 text-xs overflow-auto max-h-48">
                      {JSON.stringify(data, null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No JMX metrics available (L2+ bundles only).</p>
            )}
          </div>
        )}

        {activeTab === 'kerberos' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Kerberos Status</h2>
            {contents?.kerberosStatus ? (
              <pre className="bg-gray-50 rounded-md p-4 text-xs overflow-auto max-h-96">
                {JSON.stringify(contents.kerberosStatus, null, 2)}
              </pre>
            ) : (
              <p className="text-sm text-muted-foreground">No Kerberos data available.</p>
            )}
          </div>
        )}

        {activeTab === 'ssl' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">SSL Certificates</h2>
            {contents?.sslCerts ? (
              <div className="space-y-3">
                {Object.entries(contents.sslCerts).map(([endpoint, info]: [string, any]) => (
                  <div key={endpoint} className="border rounded-md p-3">
                    <h3 className="text-sm font-medium font-mono">{endpoint}</h3>
                    <div className="mt-1 text-xs text-muted-foreground">
                      {info.error ? (
                        <span className="text-red-600">{info.error}</span>
                      ) : (
                        <>
                          <span>Expires: {info.notAfter}</span>
                          {info.daysRemaining !== undefined && (
                            <span className={`ml-2 ${info.daysRemaining < 30 ? 'text-red-600 font-medium' : ''}`}>
                              ({info.daysRemaining} days remaining)
                            </span>
                          )}
                        </>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No SSL certificate data available.</p>
            )}
          </div>
        )}

        {activeTab === 'kernel' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Kernel Parameters</h2>
            {contents?.kernelParams ? (
              <pre className="bg-gray-50 rounded-md p-4 text-xs overflow-auto max-h-96">
                {JSON.stringify(contents.kernelParams, null, 2)}
              </pre>
            ) : (
              <p className="text-sm text-muted-foreground">No kernel parameter data available.</p>
            )}
          </div>
        )}

        {activeTab === 'drift' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Configuration Drift</h2>
            {contents?.configDrift ? (
              <div className="space-y-3">
                {Object.entries(contents.configDrift).map(([configType, drift]: [string, any]) => (
                  <div key={configType} className="border rounded-md p-3">
                    <h3 className="text-sm font-semibold">{configType}</h3>
                    {drift.drifted?.length > 0 ? (
                      <div className="mt-2 space-y-1">
                        {drift.drifted.map((d: any, i: number) => (
                          <div key={i} className="text-xs bg-yellow-50 p-2 rounded">
                            <span className="font-mono font-medium">{d.property}</span>
                            <div className="flex gap-4 mt-1">
                              <span className="text-red-600">Desired: {d.desired}</span>
                              <span className="text-green-600">Actual: {d.actual}</span>
                            </div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="text-xs text-green-600 mt-1">No drift detected</p>
                    )}
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No config drift data available (L2+ bundles only).</p>
            )}
          </div>
        )}

        {activeTab === 'analysis' && (
          <div>
            <h2 className="text-lg font-semibold mb-4">Analysis Summary</h2>
            {bundle.analysisSummary ? (
              <div className="prose prose-sm max-w-none">
                <pre className="bg-gray-50 rounded-md p-4 text-xs whitespace-pre-wrap">
                  {bundle.analysisSummary}
                </pre>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">No analysis summary available.</p>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
