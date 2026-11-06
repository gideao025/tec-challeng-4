## O que muda

<!-- Resumo em 1 a 3 frases. Link da issue/tarefa, se houver. -->

## Módulo(s) afetado(s)

- [ ] commons
- [ ] receber-avaliacao
- [ ] notificar-critico
- [ ] gerar-relatorio-semanal
- [ ] infra (compose / Kong / Keycloak / faasd / Grafana / Terraform)
- [ ] documentação / Postman

## Como foi testado

<!-- Testes novos ou alterados, comandos executados, prints (Mailpit, Grafana, Postman). -->

- [ ] `make verify` passou localmente
- [ ] Testado na cloudlab (`make deploy`), quando aplicável

## Checklist

- [ ] Contrato de entrada inalterado (`descricao`, `nota`)
- [ ] Regra de urgência só em `ClassificadorUrgencia`
- [ ] Sem segredos no código; novas variáveis em `.env.example` / `stack.yml`
- [ ] Logs via `commons.Log`, sem `descricao` nem `alunoId`
- [ ] README atualizado (funções, env vars, permissões), se o comportamento mudou
