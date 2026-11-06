# Matriz de rastreabilidade dos requisitos

Cada requisito do enunciado, onde está implementado e como foi verificado. Serve como
checklist para a banca e para o grupo antes da entrega.

Legenda da verificação: **T** teste automatizado · **E** execução real contra os serviços
(validada durante o desenvolvimento) · **C** só na cloudlab (precisa do faasd).

## Domínio e contrato

| Requisito | Implementação | Verificação |
|---|---|---|
| `POST /avaliacao` com `{descricao, nota}`, sem campos extras | `ReceberAvaliacao.processar` (lista `CAMPOS_PERMITIDOS`) · [openapi.yaml](api/openapi.yaml) | T `payloadInvalidoRetorna400Padronizado` (campos `alunoId`/`urgencia`) |
| Rota sem acento documentada | README §4 · [ADR 0009](adr/0009-rota-sem-acento.md) | n/a |
| Nota inteira 0..10 | `ReceberAvaliacao` (`isIntegralNumber`, `notaValida`) | T `notaForaDoIntervaloRetorna400`, payloads `"7"`, `7.5`, `null` |
| Descrição não vazia com limite | `ReceberAvaliacao` (`DESCRICAO_MAX`, padrão 1000) | T (vazia, espaços, número, acima do limite) |
| Erros → 400 com corpo padronizado | `ReceberAvaliacao.erro` → `{erro, mensagem, detalhes, correlationId}` | T + E |
| Urgência derivada da nota (0-3/4-6/7-10) | `commons/ClassificadorUrgencia` + enum `Urgencia` | T `ClassificadorUrgenciaTest` (0, 3, 4, 6, 7, 10, -1, 11) |
| Limites por env (`LIMITE_CRITICA=3`, `LIMITE_MODERADA=6`) | `ClassificadorUrgencia.doAmbiente` · `stack.yml` | T `limitesConfiguraveisMudamAsFaixas`, `doAmbienteUsaPadroes3e6SemVariaveis` |
| Urgência persistida na gravação | `AvaliacaoRepository.salvar` (atributo `urgencia`) · [ADR 0002](adr/0002-urgencia-derivada-e-persistida.md) | T `AvaliacaoRepositoryIT` |
| Regra documentada (faixas, por que derivar, por que persistir) | README §5 | n/a |

## Funções

| Requisito | Implementação | Verificação |
|---|---|---|
| **receber-avaliacao**: valida, classifica, grava, publica, retorna 201 `{id, urgencia, dataEnvio}` | `receber-avaliacao/` | T `caminhoFelizClassificaPersisteEPublica` · E (jar e imagem com watchdog) |
| `dataEnvio` = instante do recebimento, UTC ISO-8601 | `Instant.now(relogio)` truncado em ms | T (relógio fixo) |
| `alunoId` das claims do JWT, nunca do body | claim `sub`; o body com `alunoId` é rejeitado | T |
| Publicação via `EventPublisher` (domínio não conhece NATS) | `EventPublisher` + `NatsEventPublisher` · [ADR 0003](adr/0003-publicacao-explicita-eventpublisher.md) | T (mock) · E (3 mensagens no stream `AVALIACOES`) |
| CloudEvent `avaliacao.criada` | `commons/CloudEvent` · [asyncapi.yaml](api/asyncapi.yaml) | T · E |
| **notificar-critico**: assinante NATS | anotação `topic: avaliacao.criada` + nats-connector ([infra/faasd](../infra/faasd/docker-compose.yaml)) | C |
| Só `urgencia == CRITICA` | `NotificarCritico.processar` | T `naoNotificaUrgenciasNaoCriticas` · E (`IGNORADO`) |
| E-mail com descrição, urgência e data de envio (horário de Brasília) | `NotificarCritico.corpo` + `Datas.formatarBrasilia` | T `notificaCriticaComDescricaoUrgenciaEDataEmBrasilia` · E ([exemplo](exemplos/email-alerta-critico.txt)) |
| Idempotente (dedup pelo id) | `Deduplicador` (PutItem condicional, LWT no Alternator) | T `ehIdempotentePeloIdDaAvaliacao` · E (`DUPLICADO`) |
| **gerar-relatorio-semanal**: cron segunda 08:00 BRT | anotações `topic: cron-function`, `schedule: "0 11 * * 1"` | C |
| Cobre os 7 dias anteriores | `GerarRelatorioSemanal.processar` | T `consultaExatamenteSeteDiasDaSemanaAnterior` |
| 7 Query, nunca Scan | `AvaliacaoRepository.listarDoDia` (só `QueryRequest`; não há `scan` no código) | T (7 chamadas) · `grep -rnE 'ScanRequest|\.scan\(' */src/main` sem resultado |
| Lista com descrição, urgência, data de envio | `Relatorio.Item` | T `listaContemDescricaoUrgenciaEDataDeEnvio` |
| Quantidade por dia, com zeros | `Relatorio.consolidar` | T `contaPorDiaIncluindoDiasSemAvaliacao` |
| Quantidade por urgência, com zeros | `Relatorio.consolidar` (`EnumMap`) | T `contaPorUrgenciaIncluindoNiveisZerados` |
| Média geral | `Relatorio.consolidar` (2 casas, `HALF_UP`) | T `calculaMediaGeralComDuasCasas` |
| CSV + JSON no MinIO, chave `relatorios/<ano>-W<semana>.*` | `GerarRelatorioSemanal` + `ArmazenamentoRelatorios` | T · E (objetos listados no bucket) |
| E-mail com resumo e link do MinIO | `GerarRelatorioSemanal.corpoEmail` | T · E ([exemplo](exemplos/email-relatorio-semanal.txt)) |
| Semana vazia: relatório válido, sem erro | `Relatorio` (`mediaNotas` null) | T `semanaVaziaGeraRelatorioValidoZerado`, `semanaVaziaNaoFalhaEGeraArquivosEEmail` · E ([exemplo](exemplos/email-relatorio-semana-vazia.txt)) |

## Plataforma e infraestrutura

| Requisito | Implementação | Verificação |
|---|---|---|
| faasd com scale-to-zero | faasd 0.19.7 · `scale_from_zero=true` · idler ([ADR 0006](adr/0006-scale-to-zero-com-idler-proprio.md)) | C |
| Kong DB-less com `kong.yaml`, plugins jwt, rate-limiting, correlation-id | [infra/kong.yaml](../infra/kong.yaml) | E (`kong config parse`, 401/429, header de correlação) |
| Keycloak: realm `feedback`, roles `aluno`/`admin`, Postgres, realm JSON importado | [infra/keycloak/realm-feedback.json](../infra/keycloak/realm-feedback.json) | E (token com `realm_access.roles`) |
| ScyllaDB Alternator + AWS SDK v2 com `endpointOverride` | `commons/Clientes.dynamo` | T `AvaliacaoRepositoryIT` · E |
| MinIO + SDK S3 com `endpointOverride` | `commons/Clientes.s3` | E |
| NATS JetStream com CloudEvents | `nats-js` no overlay do faasd · [ADR 0005](adr/0005-jetstream-ao-lado-do-nats-streaming.md) | E (NATS 2.11 local) · C |
| cron-connector | overlay do faasd | C |
| Mailpit | compose | E |
| Prometheus + Grafana + Loki | Prometheus do faasd · compose (Grafana, Loki, Alloy) | E (Loki/Grafana) · C (Prometheus) |
| Java 21, of-watchdog HTTP, Javalin, sem Spring, Maven multi-módulo | `pom.xml` (`release 21`), `Dockerfile`, módulos · [ADR 0010](adr/0010-javalin-sem-spring.md) | E (`./mvnw verify`, imagem) |
| Modelagem `PK=AVALIACAO#<dia>`, `SK=<ts>#<uuid>`, atributos | `AvaliacaoRepository` · [ADR 0004](adr/0004-particao-diaria-fuso-brasilia.md) | T `AvaliacaoRepositoryIT` |
| `infra/docker-compose.yaml` com volumes nomeados e healthchecks | [infra/docker-compose.yaml](../infra/docker-compose.yaml) | E (`up --wait`: todos healthy) |
| `stack.yml` com 3 funções, limites de memória e env vars | [stack.yml](../stack.yml) | C |
| Terraform bpg/proxmox, VM `cloudlab` a partir de template cloud-init | [infra/terraform](../infra/terraform) | E (`terraform validate`) · C (`apply`) |
| `make up`, `make deploy`, `make logs`, `make destroy` | [Makefile](../Makefile) | E (`up` local) · C (`deploy`) |

## Segurança e governança

| Requisito | Implementação | Verificação |
|---|---|---|
| Kong valida JWT antes da função; nenhuma função exposta | plugin jwt + gateway em `127.0.0.1` · [ADR 0007](adr/0007-gateway-faasd-somente-loopback.md) | E (401 sem token ou com token adulterado) |
| Role `aluno` envia avaliação | `ReceberAvaliacao.temRole` | T `semRoleAlunoRetorna403` · E (newman 403) |
| Somente `admin` acessa relatórios | política `relatorio-leitura` para `admin-relatorios`; a função só grava | E (admin não grava; serviço não lê) |
| Segredos via `.env` (`.env.example` versionado) e secrets do OpenFaaS | `scripts/preparar.sh`, `make segredos`, `Config.segredo` | E |
| Bucket privado + usuário de serviço com política mínima | [infra/minio/init.sh](../infra/minio/init.sh) | E |
| Rate limiting e CORS no Kong | `kong.yaml` | E (429) |
| Tratamento LGPD documentado | [lgpd.md](lgpd.md) · README §9 | n/a |

## Observabilidade

| Requisito | Implementação | Verificação |
|---|---|---|
| Logs JSON com correlationId do Kong (utilitário no commons) | `commons/Log`, `commons/Servidor` | E (logs com `correlationId` vindo do header) |
| Coletados pelo Loki | Alloy (journald) → Loki · [config.alloy](../infra/alloy/config.alloy) | E (labels `funcao`/`nivel`) |
| Dashboard como código: invocações, erro, p95, urgência | [feedback-funcoes.json](../infra/grafana/dashboards/feedback-funcoes.json) | E (provisionado; painéis Loki) · C (painéis Prometheus) |
| Alertas: taxa de erro > 0 e pico de CRITICA | [alertas.yaml](../infra/grafana/provisioning/alerting/alertas.yaml) | E (alerta Loki disparou e-mail no Mailpit) · C (Prometheus) |

## Testes, organização e documentação

| Requisito | Onde |
|---|---|
| JUnit 5 + Mockito em todos os casos listados | `*/src/test` (57 testes) |
| Testcontainers com ScyllaDB | `commons/.../AvaliacaoRepositoryIT` |
| Um módulo por integrante, CONTRIBUTING, template de PR | módulos Maven · [CONTRIBUTING.md](../CONTRIBUTING.md) · [.github/](../.github) |
| Diagrama Mermaid, equivalência AWS/Azure/OCI, modelo de nuvem e portabilidade | README §1 a §3 |
| Trade-off DynamoDB Streams × EventPublisher | README §6 · ADR 0003 |
| Documentação das funções (gatilho, entrada, saída, env, permissões) | README §7 · READMEs dos módulos |
| Exemplos de e-mail e de relatório | [docs/exemplos/](exemplos) |
| Deploy passo a passo (terraform apply → make deploy) | README §12 |
| Monitoramento com prints | README §10 · `docs/img/` (**capturar na cloudlab**) |
| Roteiro do vídeo | README §15 |
| Collection do Postman (token, urgências, 401/400/429) | [postman/](../postman) |

## Pendências antes da entrega

- [ ] Rodar `terraform apply`, `make up` e `make deploy` na cloudlab e marcar os itens **C** acima.
- [ ] Capturar `docs/img/dashboard-grafana.png` e `docs/img/alerta-mailpit.png`.
- [ ] Preencher os nomes dos integrantes em CONTRIBUTING.md e .github/CODEOWNERS.
- [ ] Gravar o vídeo (roteiro no README §15).
