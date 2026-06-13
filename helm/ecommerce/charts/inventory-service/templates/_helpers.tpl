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
{{- $registry := required "global.imageRegistry is required (set --set global.imageRegistry=$IMAGE_REGISTRY); refusing to build an image ref without a registry" (.Values.global.imageRegistry | default .Values.image.registry) -}}
{{- $repo := .Values.image.repository -}}
{{- $tag := .Values.global.imageTag | default .Values.image.tag -}}
{{- printf "%s/%s:%s" $registry $repo $tag -}}
{{- end -}}
