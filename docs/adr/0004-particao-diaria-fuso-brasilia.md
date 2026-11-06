# 0004. Partição diária pelo fuso de Brasília

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

O enunciado fixa `PK = "AVALIACAO#<YYYY-MM-DD>"` e exige que o relatório faça 7 `Query`
(uma por dia), nunca `Scan`. Falta definir **qual fuso** determina o dia. A coordenação
trabalha no horário de Brasília, e o relatório roda segunda-feira às 08:00 BRT.

## Decisão

O dia da PK é a data de `dataEnvio` no fuso **America/Sao_Paulo** (`FUSO_NEGOCIO`).
`dataEnvio` continua gravada em **UTC** (ISO-8601). A SK é `<timestampISO UTC>#<uuid>`.

## Alternativas consideradas

- **Dia em UTC**: uma avaliação enviada no domingo às 22:00 BRT cairia na segunda-feira. O
  relatório "de segunda a domingo" incluiria parte da semana seguinte e omitiria parte da
  atual.

## Consequências

- As 7 partições consultadas correspondem exatamente aos 7 dias que a coordenação enxerga.
  O caso das 23:30 BRT é coberto por `AvaliacaoRepositoryIT`.
- Todas as escritas de um dia vão para a mesma partição (hot partition). Isso não pesa no
  volume de uma turma. Em escala, aplicar *write sharding* (`AVALIACAO#<dia>#<n>`).
