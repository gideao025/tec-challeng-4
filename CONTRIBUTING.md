# Como contribuir

Repositório do grupo para o Tech Challenge Fase 4. Cada integrante é **dono** de um módulo;
as convenções abaixo evitam conflitos e mantêm o histórico legível para a banca.

## Donos dos módulos

| Diretório | Dono | Revisor reserva |
|---|---|---|
| `commons/` | _Integrante 1_ | qualquer outro (mudança aqui afeta todos: **2 aprovações**) |
| `receber-avaliacao/` | _Integrante 2_ | Integrante 1 |
| `notificar-critico/` | _Integrante 3_ | Integrante 2 |
| `gerar-relatorio-semanal/` | _Integrante 4_ | Integrante 3 |
| `infra/`, `scripts/`, `stack.yml`, `Makefile` | _Integrante 5_ | Integrante 1 |
| `README.md`, `docs/`, `postman/` | todos | — |

Atualize os nomes acima e em [.github/CODEOWNERS](.github/CODEOWNERS) com os usuários do GitHub.

## Branches

- `main`: sempre implantável e protegida (merge só via PR, `make verify` verde e aprovação do dono).
- Uma branch por tarefa, criada a partir da `main` atualizada:

| Prefixo | Uso | Exemplo |
|---|---|---|
| `feat/<modulo>-<assunto>` | funcionalidade | `feat/notificar-critico-idempotencia` |
| `fix/<modulo>-<assunto>` | correção | `fix/receber-avaliacao-nota-decimal` |
| `infra/<assunto>` | compose, Kong, Keycloak, Terraform, Grafana | `infra/grafana-alerta-pico` |
| `docs/<assunto>` | documentação | `docs/tabela-equivalencia` |
| `test/<modulo>-<assunto>` | só testes | `test/relatorio-semana-vazia` |

Mantenha a branch curta (menos de 2 dias) e faça `git rebase origin/main` antes de abrir o PR.

## Commits: Conventional Commits em português

```
<tipo>(<escopo>): <resumo no imperativo, até 72 caracteres>

[corpo opcional: o porquê da mudança]
```

- **tipos**: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`, `ci`, `infra`
- **escopo**: o módulo ou a peça de infra: `commons`, `receber-avaliacao`,
  `notificar-critico`, `gerar-relatorio-semanal`, `kong`, `keycloak`, `faasd`, `grafana`,
  `terraform`, `postman`

Exemplos:

```
feat(receber-avaliacao): rejeitar campos fora do contrato
fix(commons): usar fuso de Brasília na partição diária
infra(kong): limitar payload a 16 KB
docs: adicionar roteiro do vídeo
```

## Pull requests

1. Rode `make verify` localmente (testes + `docker compose config`).
2. Abra o PR usando o template (preenchido automaticamente).
3. Um PR por assunto; não misture refatoração com funcionalidade.
4. O dono do módulo aprova; `commons/` exige 2 aprovações.
5. Merge por **squash**, com o título do PR no formato Conventional Commits.

## Regras do projeto

- Contrato de entrada **fixo**: `{ "descricao", "nota" }`. Não adicione campos.
- A regra de urgência vive **apenas** em `commons/ClassificadorUrgencia`.
- Nada de segredo no código ou no git: use `.env` (gerado) e os secrets do OpenFaaS.
- Logs só via `commons.Log` (JSON com `correlationId`). Não registre `descricao` nem `alunoId`.
- Código, testes, commits e documentação em português.
