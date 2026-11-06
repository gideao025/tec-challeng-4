# 0006. Scale-to-zero com idler próprio

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

O gateway do OpenFaaS CE acorda funções com zero réplicas (`scale_from_zero=true`), mas o
componente que **reduz** a zero (`faas-idler`) é exclusivo do OpenFaaS Pro.

## Decisão

Criar o `faasd-idler` ([infra/faasd/idler.sh](../../infra/faasd/idler.sh)), executado a
cada minuto por um *timer* do systemd. Para cada função com o label
`com.openfaas.scale.zero=true`, ele consulta no Prometheus as invocações dos últimos
`IDLE_MIN` minutos (padrão 15). Se for zero, chama `POST /system/scale-function/<nome>`
com `replicas: 0`.

## Consequências

- O comportamento é equivalente ao de uma Lambda ou Azure Function: sem tráfego, nenhum
  processo ocupa memória. O painel "Réplicas ativas" mostra isso acontecendo.
- A primeira chamada após ociosidade paga um *cold start* da JVM (cerca de 1 a 2 s),
  mitigado por `-XX:TieredStopAtLevel=1` e `SerialGC`. Se isso incomodar, aumentar
  `IDLE_MIN` ou retirar o label da função.
- O script é simples: depende de `curl` e `jq` e do Prometheus estar no ar. Se o
  Prometheus falhar, nada é escalado, o que é um modo de falha seguro.
