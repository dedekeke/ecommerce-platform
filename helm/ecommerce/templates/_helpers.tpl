{{/*
Common labels for resources owned by the umbrella chart.
*/}}
{{- define "ecommerce.umbrellaLabels" -}}
app.kubernetes.io/part-of: ecommerce-platform
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end -}}
