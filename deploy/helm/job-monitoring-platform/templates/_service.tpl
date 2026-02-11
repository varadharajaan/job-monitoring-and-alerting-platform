{{/*
  Generic Deployment + Service template.
  Invoked once per service entry in values.yaml.
  Usage: {{ include "jobmonitor.service" (dict "name" "auth-service" "svc" .Values.services.authService "Values" .Values "Release" .Release "Chart" .Chart) }}
*/}}
{{- define "jobmonitor.service" -}}
{{- if .svc.enabled }}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ .name }}
  namespace: {{ .Values.global.namespace }}
  labels:
    {{- include "jobmonitor.labels" (dict "name" .name "Release" .Release "Chart" .Chart "Values" .Values) | nindent 4 }}
spec:
  replicas: {{ .svc.replicas }}
  selector:
    matchLabels:
      {{- include "jobmonitor.selectorLabels" (dict "name" .name "Release" .Release) | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "jobmonitor.selectorLabels" (dict "name" .name "Release" .Release) | nindent 8 }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: {{ .svc.port | quote }}
    spec:
      {{- with .Values.global.imagePullSecrets }}
      imagePullSecrets:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      containers:
        - name: {{ .name }}
          image: {{ include "jobmonitor.image" (dict "image" .svc.image "Values" .Values) }}
          imagePullPolicy: {{ .Values.global.imagePullPolicy }}
          ports:
            - containerPort: {{ .svc.port }}
          envFrom:
            - configMapRef:
                name: jobmonitor-config
            - secretRef:
                name: jobmonitor-secrets
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: {{ .svc.port }}
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: {{ .svc.port }}
            initialDelaySeconds: 60
            periodSeconds: 30
          resources:
            {{- toYaml .svc.resources | nindent 12 }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ .name }}
  namespace: {{ .Values.global.namespace }}
  labels:
    {{- include "jobmonitor.labels" (dict "name" .name "Release" .Release "Chart" .Chart "Values" .Values) | nindent 4 }}
spec:
  selector:
    {{- include "jobmonitor.selectorLabels" (dict "name" .name "Release" .Release) | nindent 4 }}
  ports:
    - port: {{ .svc.port }}
      targetPort: {{ .svc.port }}
  type: ClusterIP
{{- end }}
{{- end -}}
