# Documentação

O [README principal](../README.md) é a visão geral exigida pelo enunciado: arquitetura,
equivalência com nuvens públicas, regra de urgência, funções, deploy e roteiro do vídeo.
Esta pasta aprofunda cada tema.

| Documento | Para quem | Conteúdo |
|---|---|---|
| [requisitos.md](requisitos.md) | banca, grupo | matriz requisito → implementação → verificação; pendências da entrega |
| [api/openapi.yaml](api/openapi.yaml) | consumidores da API | contrato OpenAPI 3.1 de `POST /avaliacao` (exemplos, erros, segurança) |
| [api/asyncapi.yaml](api/asyncapi.yaml) | quem consome eventos | AsyncAPI 3.0 do CloudEvent `avaliacao.criada` |
| [adr/](adr/README.md) | arquitetos, banca | 10 decisões de arquitetura com contexto, alternativas e consequências |
| [arquitetura/](arquitetura/arquitetura.html) | todos | diagrama da solução (abrir `arquitetura.html` no navegador; regenerar com `python3 arquitetura/gerar.py`); prévia em `img/arquitetura.png` |
| [operacao.md](operacao.md) | quem opera a cloudlab | saúde, logs, reprocessamento, rotação de segredos, backup, solução de problemas |
| [lgpd.md](lgpd.md) | DPO, banca | inventário de dados, princípios, base legal, direitos do titular, retenção, riscos |
| [exemplos/](exemplos) | todos | e-mails e relatórios **gerados pelas funções reais** |
| [img/](img) | todos | prints do dashboard e do Mailpit (capturar na cloudlab) |
| READMEs dos módulos: [commons](../commons/README.md) · [receber-avaliacao](../receber-avaliacao/README.md) · [notificar-critico](../notificar-critico/README.md) · [gerar-relatorio-semanal](../gerar-relatorio-semanal/README.md) | dono de cada módulo | classes, regras, variáveis e como rodar localmente |
| [CONTRIBUTING.md](../CONTRIBUTING.md) | grupo | branches, commits, PRs e donos dos módulos |

Para visualizar os contratos: cole o YAML em <https://editor.swagger.io> (OpenAPI) ou
<https://studio.asyncapi.com> (AsyncAPI), ou rode
`docker run --rm -v $PWD/docs/api:/spec redocly/cli lint /spec/openapi.yaml`.

## Glossário

| Termo | Significado neste projeto |
|---|---|
| **Avaliação** | registro `{descricao, nota}` enviado por um aluno sobre uma aula |
| **Urgência** | CRITICA / MODERADA / BAIXA, derivada da nota e gravada no recebimento |
| **cloudlab** | VM Ubuntu 24.04 no Proxmox que hospeda toda a plataforma |
| **faasd** | OpenFaaS sem Kubernetes: gateway, provider e funções sobre containerd |
| **of-watchdog (modo HTTP)** | processo que recebe a invocação na porta 8080 e repassa ao servidor Java mantido vivo |
| **Scale-to-zero** | função sem réplicas quando ociosa; o gateway a reativa na próxima chamada (*cold start*) |
| **Alternator** | API compatível com DynamoDB do ScyllaDB |
| **PK / SK** | chave de partição (`AVALIACAO#<dia>`) e de ordenação (`<timestamp>#<uuid>`) |
| **LWT** | *lightweight transaction* do Scylla; viabiliza a escrita condicional da deduplicação |
| **JetStream** | camada de persistência do NATS; o stream `AVALIACOES` retém eventos por 30 dias |
| **CloudEvents** | especificação CNCF de envelope de eventos (`specversion`, `id`, `type`, `data`…) |
| **Connector** | componente do OpenFaaS que traduz uma fonte (NATS, cron) em invocações de função |
| **correlationId** | UUID gerado pelo Kong (`X-Correlation-ID`) e propagado a logs e eventos |
| **Semana ISO** | numeração ISO-8601 (`2026-W45`): semanas de segunda a domingo |
| **HOST_INTERNO** | IP da `docker0` (172.17.0.1), alcançável pelas funções e não pela LAN |
| **Dual write** | gravar no banco e publicar no broker como duas operações não atômicas (ver ADR 0003) |
