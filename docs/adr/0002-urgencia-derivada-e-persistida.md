# 0002. Urgência derivada da nota e persistida na gravação

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

O contrato de entrada tem apenas `descricao` e `nota`, mas o sistema precisa de uma
urgência para decidir alertas e montar o relatório.

## Decisão

- A urgência é **derivada da nota** por `ClassificadorUrgencia` (módulo `commons`): 0 a
  `LIMITE_CRITICA` → CRITICA; até `LIMITE_MODERADA` → MODERADA; acima → BAIXA. Os padrões
  são 3 e 6, vindos de variáveis de ambiente.
- A urgência é **calculada uma vez, no recebimento, e gravada** junto com a avaliação.
  Consumidores (notificação, relatório) leem o valor gravado; não recalculam.

## Alternativas consideradas

- **Cliente informa a urgência**: viola o contrato e permite manipular alertas.
- **Calcular na leitura**: alterar os limites reclassificaria o histórico. Relatórios
  passados mudariam, e alertas já enviados deixariam de bater com os dados.

## Consequências

- A regra fica num só lugar e é testada em todas as fronteiras (`ClassificadorUrgenciaTest`).
- Mudar a regra afeta só as avaliações novas, o que é auditável.
- Se um dia for preciso reclassificar o histórico, isso exige uma migração explícita, de
  propósito.
