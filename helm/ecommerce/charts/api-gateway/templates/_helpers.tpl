{{- define "svc.name" -}}
{{- .Values.service.name -}}
{{- end -}}

{{- define "svc.labels" -}}
app: {{ .Values.service.name }}
app.kubernetes.io/name: {{ .Values.service.name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
tier: backend
{{- end -}}

{{- define "svc.image" -}}
{{- printf "%s:%s" .Values.image.repository (.Values.global.imageTag | default .Values.image.tag) -}}
{{- end -}}
