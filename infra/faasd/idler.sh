#!/usr/bin/env bash
# Scale-to-zero para o faasd CE (o faas-idler é recurso do OpenFaaS Pro).
# Funções com o label com.openfaas.scale.zero=true e nenhuma invocação nos últimos
# IDLE_MIN minutos são escaladas para 0 réplicas; o gateway (scale_from_zero=true)
# as reativa na próxima chamada. Executado a cada minuto pelo faasd-idler.timer.
set -euo pipefail
GW="${GATEWAY:-http://127.0.0.1:8080}"
PROM="${PROMETHEUS:-http://127.0.0.1:9090}"
IDLE_MIN="${IDLE_MIN:-15}"
AUTH="admin:$(cat /var/lib/faasd/secrets/basic-auth-password)"

curl -sf -u "$AUTH" "$GW/system/functions" |
  jq -r '.[] | select(.labels["com.openfaas.scale.zero"]=="true" and .replicas>0) | .name' |
  while read -r fn; do
    q="sum(increase(gateway_function_invocation_total{function_name=~\"${fn}(\\\\..*)?\"}[${IDLE_MIN}m]))"
    n="$(curl -sf --data-urlencode "query=$q" "$PROM/api/v1/query" | jq -r '.data.result[0].value[1] // "0"')"
    if awk "BEGIN{exit !($n < 1)}"; then
      curl -sf -u "$AUTH" -X POST "$GW/system/scale-function/$fn" \
        -d "{\"serviceName\":\"$fn\",\"replicas\":0}" && echo "idler: $fn -> 0 réplicas (ocioso há ${IDLE_MIN}m)"
    fi
  done
