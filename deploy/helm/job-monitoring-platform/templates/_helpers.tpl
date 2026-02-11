{{/*
Common labels applied to all resources
*/}}
{{- define "jobmonitor.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ $.Release.Name }}
app.kubernetes.io/version: {{ $.Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ $.Release.Service }}
app.kubernetes.io/part-of: job-monitoring-platform
helm.sh/chart: {{ $.Chart.Name }}-{{ $.Chart.Version }}
{{- end -}}

{{/*
Selector labels for a service
*/}}
{{- define "jobmonitor.selectorLabels" -}}
app: {{ .name }}
app.kubernetes.io/instance: {{ $.Release.Name }}
{{- end -}}

{{/*
Full image reference: registry/image:tag
*/}}
{{- define "jobmonitor.image" -}}
{{- if .Values.global.imageRegistry -}}
{{ .Values.global.imageRegistry }}/{{ .image }}:{{ .Values.global.imageTag }}
{{- else -}}
{{ .image }}:{{ .Values.global.imageTag }}
{{- end -}}
{{- end -}}
