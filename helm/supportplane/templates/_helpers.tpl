{{/*
Expand the name of the chart.
*/}}
{{- define "supportplane.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "supportplane.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "supportplane.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/part-of: supportplane
{{ include "supportplane.selectorLabels" . }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "supportplane.selectorLabels" -}}
app.kubernetes.io/name: {{ include "supportplane.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Name of the Secret holding DB + Keycloak admin + encryption key credentials.
Use an externally-managed Secret when secrets.existingSecret is set.
*/}}
{{- define "supportplane.secretName" -}}
{{- if .Values.secrets.existingSecret }}
{{- .Values.secrets.existingSecret }}
{{- else }}
{{- printf "%s-secrets" (include "supportplane.fullname" .) }}
{{- end }}
{{- end }}

{{/*
ServiceAccount name.
*/}}
{{- define "supportplane.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "supportplane.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
PostgreSQL hostname — the in-chart StatefulSet when enabled, otherwise the
externally-provided host.
*/}}
{{- define "supportplane.postgresHost" -}}
{{- if .Values.postgres.enabled }}
{{- printf "%s-postgres" (include "supportplane.fullname" .) }}
{{- else }}
{{- required "postgres.external.host is required when postgres.enabled=false" .Values.postgres.external.host }}
{{- end }}
{{- end }}

{{/*
PostgreSQL port.
*/}}
{{- define "supportplane.postgresPort" -}}
{{- if .Values.postgres.enabled }}5432{{ else }}{{ .Values.postgres.external.port | default 5432 }}{{ end }}
{{- end }}

{{/*
Keycloak internal URL used by the backend. Always resolves to the in-cluster
Service name when keycloak.enabled=true.
*/}}
{{- define "supportplane.keycloakInternalUrl" -}}
{{- printf "http://%s-keycloak:8080" (include "supportplane.fullname" .) }}
{{- end }}
