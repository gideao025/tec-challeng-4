# 0003. Publicação explícita via `EventPublisher` em vez de Streams

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

Na AWS, a notificação seria disparada por **DynamoDB Streams → Lambda**: a gravação gera
o evento de forma atômica. No ScyllaDB Alternator, Streams é experimental, e o faasd não
tem gatilho equivalente ao *event source mapping*.

## Decisão

A função `receber-avaliacao` grava e, em seguida, **publica** o CloudEvent
`avaliacao.criada` por meio da interface de domínio `EventPublisher`. A implementação
`NatsEventPublisher` usa JetStream com confirmação (ack) e `Nats-Msg-Id` = id do evento.

## Alternativas consideradas

- **Alternator Streams (CDC)**: experimental e sem connector para o OpenFaaS.
- **Transactional outbox**: garante atomicidade, mas exige uma tabela de outbox e um relay.
  É complexidade além do necessário para o volume atual.
- **Notificar de forma síncrona dentro da `receber-avaliacao`**: acopla responsabilidades
  e aumenta a latência da API.

## Consequências

- Existe *dual write*: se o NATS falhar depois da gravação, o alerta pode não sair. As
  mitigações são: a gravação vem antes; o log `evento_nao_publicado` (nível ERROR)
  dispara alerta no Grafana; a avaliação aparece no relatório semanal; é possível
  reprocessar manualmente (ver [operacao.md](../operacao.md#reprocessar-um-evento)).
- O domínio não conhece o NATS. Migrar para EventBridge, Event Grid ou Streams é uma nova
  implementação da interface.
- Se a garantia de alerta virar requisito forte, adotar outbox sem mudar `EventPublisher`.
