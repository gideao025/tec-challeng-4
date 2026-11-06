# 0005. NATS JetStream ao lado do NATS Streaming do faasd

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

A stack exige NATS JetStream. O faasd CE 0.19.7 embarca o **NATS Streaming** (STAN,
descontinuado), usado pelo `queue-worker` 0.14.2 nas invocações assíncronas. Trocar a imagem
por um NATS 2.x com JetStream quebraria essa fila.

## Decisão

Adicionar ao compose do faasd ([infra/faasd/docker-compose.yaml](../../infra/faasd/docker-compose.yaml))
um serviço **`nats-js`** (NATS 2.11 com `--jetstream`) para os eventos de domínio, e o
**nats-connector** apontando para ele. A fila interna do faasd não é alterada.

## Consequências

- O JetStream retém os eventos (30 dias) para auditoria e replay. O nats-connector assina o
  subject como um assinante *core*, e as mensagens publicadas no stream também chegam a ele.
- Rodam dois servidores NATS (cerca de 20 MB a mais). Quando o faasd adotar JetStream, os
  dois podem ser unificados.
- As funções resolvem `nats-js` pelo `/etc/hosts` que o faasd injeta (`/var/lib/faasd/hosts`).
