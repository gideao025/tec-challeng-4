# commons

Biblioteca compartilhada pelas três funções. Tudo que é **regra de negócio comum** ou
**infraestrutura repetida** fica aqui, para não ser reimplementado em cada função.

> Mudanças aqui afetam todas as funções: o PR exige **2 aprovações** (ver CONTRIBUTING.md).

## Classes

| Classe | Responsabilidade |
|---|---|
| `Urgencia` | enum `CRITICA`, `MODERADA`, `BAIXA` |
| `ClassificadorUrgencia` | **fonte única** da regra nota → urgência; limites `LIMITE_CRITICA`/`LIMITE_MODERADA` (padrões 3 e 6) |
| `Avaliacao` | record persistido (`id`, `alunoId`, `descricao`, `nota`, `urgencia`, `dataEnvio`) |
| `AvaliacaoRepository` | `salvar` (PutItem) e `listarDoDia` (Query por partição) na tabela `avaliacoes` |
| `CloudEvent` | envelope CloudEvents 1.0; `avaliacaoCriada(...)` remove o `alunoId` |
| `Clientes` | `DynamoDbClient` (Alternator) e `S3Client` (MinIO) com `endpointOverride`; `garantirTabela` |
| `EnviadorEmail` | SMTP texto (Jakarta Mail / Angus) |
| `Config` | leitura de env vars e secrets do OpenFaaS (`/var/openfaas/secrets/<nome>`, com fallback para a env var equivalente) |
| `Log` | log JSON em uma linha, com `correlationId` do thread atual |
| `Datas` | fuso de negócio (`FUSO_NEGOCIO`, padrão America/Sao_Paulo) e formatação em horário de Brasília |
| `Json` | `ObjectMapper` compartilhado (datas ISO, nulos omitidos) |
| `Servidor` | Javalin na porta `PORTA` (8082): correlationId, `/health`, erro 500 padronizado |

## Variáveis lidas aqui

| Variável | Padrão | Usada por |
|---|---|---|
| `ALTERNATOR_ENDPOINT` | obrigatória | `Clientes.dynamo` |
| `S3_ENDPOINT` | obrigatória (relatório) | `Clientes.s3` |
| `AWS_REGION` | `us-east-1` | clientes AWS |
| `TABELA_AVALIACOES` | `avaliacoes` | `AvaliacaoRepository.doAmbiente` |
| `LIMITE_CRITICA` / `LIMITE_MODERADA` | `3` / `6` | `ClassificadorUrgencia.doAmbiente` |
| `FUSO_NEGOCIO` | `America/Sao_Paulo` | `Datas` |
| `SMTP_HOST`, `SMTP_PORT`, `EMAIL_REMETENTE` | `—`, `1025`, `—` | `EnviadorEmail.doAmbiente` |
| `PORTA` | `8082` | `Servidor` |
| secrets `alternator-access-key`/`-secret-key`, `minio-access-key`/`-secret-key` | — | `Clientes` |

Fora do faasd, os secrets viram env vars: `alternator-secret-key` → `ALTERNATOR_SECRET_KEY`.

## Testes

```bash
./mvnw -pl commons verify   # ClassificadorUrgenciaTest + AvaliacaoRepositoryIT (Testcontainers, precisa de Docker)
```

O IT sobe `scylladb/scylla:6.2` com Alternator. Se não houver Docker, ele é ignorado
(`assumeTrue`), sem falhar.
