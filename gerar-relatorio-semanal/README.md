# gerar-relatorio-semanal

Consolida os 7 dias anteriores em CSV + JSON no MinIO e envia o resumo por e-mail.

| | |
|---|---|
| Gatilho | cron-connector: anotações `topic: cron-function`, `schedule: "0 11 * * 1"` (segunda 08:00 BRT, cron em UTC) |
| Classes | `Main` · `GerarRelatorioSemanal` (caso de uso) · `Relatorio` (consolidação pura + CSV) · `ArmazenamentoRelatorios` (S3) |
| Entrada | nenhuma; opcional `?data=AAAA-MM-DD` (data de referência; período = 7 dias anteriores) |
| Saída | `relatorios/<ano>-W<semana>.json` e `.csv` · e-mail · `200 {semana, totalAvaliacoes, mediaNotas, quantidadePorUrgencia}` · `400 DATA_INVALIDA` |
| Env | `ALTERNATOR_ENDPOINT`, `TABELA_AVALIACOES`, `S3_ENDPOINT`, `BUCKET_RELATORIOS`, `MINIO_CONSOLE_URL`, `SMTP_HOST`, `SMTP_PORT`, `EMAIL_REMETENTE`, `EMAIL_ADMINS` |
| Secrets | `alternator-*`, `minio-access-key`, `minio-secret-key` (usuário `svc-relatorio`: só `PutObject` em `relatorios/*`) |

## Regras

- **7 `Query`**, uma por partição `AVALIACAO#<dia>`, nunca `Scan`.
- Quantidade por dia inclui os 7 dias (zeros); por urgência inclui os 3 níveis (zeros).
- Média com 2 casas (`HALF_UP`); `null` no JSON e "sem avaliações" no e-mail quando a semana está vazia.
- Semana ISO-8601 do primeiro dia do período (`2026-W45` = 02/11 a 08/11/2026).
- O relatório **não** contém `alunoId`.
- Formato do CSV: seções separadas por linha em branco (resumo, por dia, por urgência,
  avaliações), RFC 4180 ([exemplo](../docs/exemplos/relatorio-2026-W44.csv)).

## Rodar localmente

```bash
set -a; . ./.env; set +a
./mvnw -q -pl gerar-relatorio-semanal -am package -DskipTests
ALTERNATOR_ENDPOINT=http://172.17.0.1:8200 ALTERNATOR_ACCESS_KEY=x ALTERNATOR_SECRET_KEY=y \
S3_ENDPOINT=http://172.17.0.1:9000 MINIO_ACCESS_KEY=$MINIO_SVC_ACCESS_KEY MINIO_SECRET_KEY=$MINIO_SVC_SECRET_KEY \
BUCKET_RELATORIOS=$MINIO_BUCKET MINIO_CONSOLE_URL=http://localhost:9001 \
SMTP_HOST=172.17.0.1 EMAIL_REMETENTE=feedback@cloudlab.local EMAIL_ADMINS=coordenacao@fiap.local \
java -jar gerar-relatorio-semanal/target/app.jar

curl -X POST "localhost:8082/?data=$(date -d tomorrow +%F)"   # inclui as avaliações de hoje
```

## Testes

`RelatorioTest` (contagens, zeros, média, semana vazia, CSV) e `GerarRelatorioSemanalTest`
(exatamente 7 consultas, chaves no MinIO, e-mail, semana vazia).
