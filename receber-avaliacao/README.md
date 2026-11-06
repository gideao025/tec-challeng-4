# receber-avaliacao

Valida, classifica, persiste e publica. Contrato: [docs/api/openapi.yaml](../docs/api/openapi.yaml).

| | |
|---|---|
| Gatilho | `POST /avaliacao` no Kong → `/function/receber-avaliacao` |
| Classes | `Main` (composition root) · `ReceberAvaliacao` (caso de uso, sem HTTP) · `EventPublisher` (porta) · `NatsEventPublisher` (JetStream) |
| Saída | `201` / `400` / `401` / `403`; evento `avaliacao.criada` ([asyncapi.yaml](../docs/api/asyncapi.yaml)) |
| Env | `ALTERNATOR_ENDPOINT`, `TABELA_AVALIACOES`, `LIMITE_CRITICA`, `LIMITE_MODERADA`, `DESCRICAO_MAX` (1000), `NATS_URL` (`nats://nats-js:4222`) |
| Secrets | `alternator-access-key`, `alternator-secret-key` |

## Fluxo de `ReceberAvaliacao.processar`

1. Lê as claims do JWT (o Kong já validou assinatura e expiração): sem `sub` → 401; sem a
   role `aluno` em `realm_access.roles` → 403.
2. Valida o corpo: objeto JSON, só `descricao` e `nota`, descrição não vazia até o limite,
   nota inteira de 0 a 10. Todas as violações vão em `detalhes` (400).
3. Classifica com `ClassificadorUrgencia`, grava com `dataEnvio = agora (UTC, ms)`.
4. Publica `avaliacao.criada`. Se o NATS falhar, loga `evento_nao_publicado` (ERROR) e
   **ainda responde 201**, porque a avaliação já está gravada ([ADR 0003](../docs/adr/0003-publicacao-explicita-eventpublisher.md)).

A conexão com o NATS é aberta sob demanda: a função sobe mesmo com o NATS fora do ar.

## Rodar localmente

Com os serviços de apoio no ar (`make up` ou `docker compose ... up -d scylla`) e um NATS com JetStream:

```bash
docker run -d --rm --name nats -p 4222:4222 nats:2.11-alpine --jetstream
./mvnw -q -pl receber-avaliacao -am package -DskipTests
ALTERNATOR_ENDPOINT=http://172.17.0.1:8200 ALTERNATOR_ACCESS_KEY=x ALTERNATOR_SECRET_KEY=y \
NATS_URL=nats://localhost:4222 java -jar receber-avaliacao/target/app.jar

# JWT de teste (a assinatura não é checada pela função, só pelo Kong)
B=$(printf '{"sub":"aluno-local","realm_access":{"roles":["aluno"]}}' | base64 -w0 | tr '+/' '-_' | tr -d '=')
curl -X POST localhost:8082/ -H "Authorization: Bearer x.$B.x" -d '{"descricao":"teste","nota":2}'
```

## Testes

`ReceberAvaliacaoTest`: caminho feliz, 15 payloads inválidos, nota fora do intervalo,
401, 403, falha de publicação sem perder a gravação.
