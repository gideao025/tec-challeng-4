# Runbook de operação

Guia de quem opera a `cloudlab` no dia a dia: verificar saúde, ler logs, reprocessar,
rotacionar segredos e resolver os problemas mais prováveis. Os comandos rodam **na VM**, em
`/opt/feedback`, salvo indicação.

```bash
alias dc='docker compose --env-file .env -f infra/docker-compose.yaml'
export OPENFAAS_URL=http://127.0.0.1:8080
```

## Sumário

- [Verificação de saúde](#verificação-de-saúde)
- [Logs](#logs)
- [Operações comuns](#operações-comuns)
- [Reprocessar um evento](#reprocessar-um-evento)
- [Segredos e rotação](#segredos-e-rotação)
- [Backup e restauração](#backup-e-restauração)
- [Solução de problemas](#solução-de-problemas)

---

## Verificação de saúde

| O quê | Comando | Esperado |
|---|---|---|
| Serviços de apoio | `dc ps` | todos `healthy` (Loki só `Up`: a imagem não tem shell para healthcheck; quem confere o Loki é o Alloy) |
| faasd | `systemctl is-active faasd faasd-provider` | `active` `active` |
| Serviços centrais do faasd | `sudo ctr -n openfaas tasks ls` | gateway, prometheus, nats, nats-js, queue-worker, nats-connector, cron-connector em `RUNNING` |
| Funções | `faas-cli list` | 3 funções; réplicas podem estar em 0 (scale-to-zero) |
| Idler | `systemctl list-timers faasd-idler.timer` | próxima execução em menos de 1 min |
| Rede faasd → Docker | `systemctl is-active cloudlab-rede` | `active` |
| Fluxo ponta a ponta | collection do Postman (pasta 1) | `201` + e-mail no Mailpit para a nota 2 |

Teste rápido sem Postman:

```bash
set -a; . ./.env; set +a
TOKEN=$(curl -s -d client_id=feedback-app -d grant_type=password -d username=aluno.demo \
  -d password="$KC_SENHA_ALUNO_DEMO" "$KEYCLOAK_URL/realms/feedback/protocol/openid-connect/token" | jq -r .access_token)
curl -i -X POST http://127.0.0.1:8000/avaliacao -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"descricao":"teste de fumaça","nota":2}'
```

## Logs

| Fonte | Comando |
|---|---|
| Uma função | `make logs F=receber-avaliacao` (ou `faas-cli logs <função> --tail`) |
| Serviço de apoio | `make logs S=kong` · `S=keycloak` · `S=scylla` … |
| Serviço central do faasd | `sudo journalctl -t openfaas:gateway -f` · `openfaas:nats-connector` · `openfaas:cron-connector` |
| faasd em si | `sudo journalctl -u faasd -u faasd-provider -f` |
| Idler | `sudo journalctl -u faasd-idler -n 50` |
| Tudo, por requisição | Grafana → dashboard "Feedback de Aulas - Funções" → campo **correlationId** |

Consultas úteis no Loki (Grafana → Explore):

```logql
{funcao="receber-avaliacao"} | json | evento="avaliacao_registrada" | urgencia="CRITICA"
{job="faasd", nivel="ERROR"}
{job="faasd"} | json | correlationId="<valor do header X-Correlation-ID>"
{funcao="notificar-critico"} | json | evento=~"notificacao_.*" | line_format "{{.evento}} {{.id}}"
```

Eventos de log emitidos pelas funções:

| Evento | Função | Nível | Significado |
|---|---|---|---|
| `tabela_criada` | todas | INFO | tabela criada no primeiro start |
| `avaliacao_registrada` | receber-avaliacao | INFO | gravada (campos `id`, `nota`, `urgencia`) |
| `avaliacao_rejeitada` | receber-avaliacao | INFO | 400 (campo `motivos`) |
| `evento_nao_publicado` | receber-avaliacao | **ERROR** | gravou mas não publicou no NATS. Reprocesse (ver abaixo) |
| `notificacao_enviada` / `_duplicada` / `_ignorada` | notificar-critico | INFO | resultado do processamento |
| `relatorio_gerado` | gerar-relatorio-semanal | INFO | semana, total e média |
| `erro_interno` | todas | **ERROR** | exceção não tratada (resposta 500) |

## Operações comuns

**Novo deploy de uma função** (após um merge):

```bash
git pull
sed -i 's/^TAG=.*/TAG=0.1.1/' .env
make deploy
```

**Gerar o relatório fora do horário** (demonstração ou reprocessamento):

```bash
# relatório da semana ISO anterior a hoje
curl -X POST http://127.0.0.1:8080/function/gerar-relatorio-semanal
# período = 7 dias antes da data informada (ex.: semana atual até ontem)
curl -X POST "http://127.0.0.1:8080/function/gerar-relatorio-semanal?data=$(date +%F)"
```

**Mudar os limites de urgência** (afeta só avaliações **novas**, ver
[ADR 0002](adr/0002-urgencia-derivada-e-persistida.md)): edite `LIMITE_CRITICA` e
`LIMITE_MODERADA` em `stack.yml` e rode `faas-cli deploy -f stack.yml --filter receber-avaliacao`.
Exige `0 ≤ LIMITE_CRITICA < LIMITE_MODERADA < 10`; caso contrário, a função não sobe.

**Mudar o horário do relatório**: altere a anotação `schedule` (em **UTC**) no `stack.yml` e
faça o deploy. O cron-connector relê as anotações em segundos.

**Desligar o scale-to-zero de uma função**: remova o label `com.openfaas.scale.zero` no
`stack.yml` e faça o deploy. Para desligar de todas: `sudo systemctl disable --now faasd-idler.timer`.

**Ajustar o rate limit**: edite `config.minute` / `config.hour` do plugin `rate-limiting` em
`infra/kong.yaml` e rode `scripts/preparar.sh && dc restart kong`.

## Reprocessar um evento

O stream `AVALIACOES` guarda os eventos por 30 dias. O NATS com JetStream (`nats-js`) só é
acessível pela rede do faasd; use o `nats-box` na rede do host apontando para o IP do serviço:

```bash
NATS_IP=$(awk '$2=="nats-js"{print $1}' /var/lib/faasd/hosts)
nats() { docker run --rm --network host natsio/nats-box:latest nats --server "$NATS_IP:4222" "$@"; }

nats stream info AVALIACOES                       # quantidade de mensagens e intervalo de sequência
nats stream get AVALIACOES 42 -j | jq -r .data | base64 -d | jq .   # inspeciona o evento 42

# reentrega o evento 42 ao notificar-critico (seguro: a deduplicação impede um segundo e-mail)
nats stream get AVALIACOES 42 -j | jq -r .data | base64 -d | faas-cli invoke notificar-critico
```

**Avaliação gravada sem evento** (log `evento_nao_publicado`): o evento não existe no stream.
Monte-o a partir do item da tabela e invoque a função:

```bash
set -a; . ./.env; set +a
ID=<id do log>; DIA=<AAAA-MM-DD em Brasília>
docker run --rm --network host -e AWS_ACCESS_KEY_ID=x -e AWS_SECRET_ACCESS_KEY=y amazon/aws-cli \
  dynamodb query --endpoint-url "http://$HOST_INTERNO:8200" --region us-east-1 --table-name avaliacoes \
  --key-condition-expression 'PK = :pk' --expression-attribute-values "{\":pk\":{\"S\":\"AVALIACAO#$DIA\"}}" \
  | jq --arg id "$ID" '.Items[] | select(.id.S==$id) | {specversion:"1.0",id:("replay-"+.id.S),
      source:"/operacao/replay",type:"avaliacao.criada",correlationid:"replay",
      data:{id:.id.S,descricao:.descricao.S,nota:(.nota.N|tonumber),urgencia:.urgencia.S,dataEnvio:.dataEnvio.S}}' \
  | faas-cli invoke notificar-critico
```

## Segredos e rotação

| Segredo | Onde vive | Como rotacionar |
|---|---|---|
| Senhas de Postgres, Grafana e admin do Keycloak | `.env` | editar o `.env` e `dc up -d`. **Atenção**: Postgres e Keycloak só aplicam a senha inicial no **primeiro** boot; depois, troque no próprio serviço (`ALTER USER`, console do Keycloak) |
| Usuários do MinIO (`svc-relatorio`, `admin-relatorios`) | `.env` | editar o `.env`, rodar `dc run --rm minio-init` (redefine as senhas) e, para o serviço, `make segredos` + `make deploy` |
| Credenciais do Alternator / MinIO nas funções | secrets do OpenFaaS | `make segredos`, depois `faas-cli deploy -f stack.yml` para remontar |
| Chave RSA do realm | `infra/.gerado/jwt-privada.pem` + `KC_RSA_PRIVATE_KEY` | o realm só é **importado no primeiro boot**. Para rotacionar: gerar o novo par, cadastrar como novo *key provider* `rsa` no console do Keycloak (prioridade maior), atualizar `jwt-publica.pem`, rodar `scripts/preparar.sh && dc restart kong` e, depois de 5 min (expiração dos tokens antigos), desativar a chave antiga |
| Senha do gateway OpenFaaS | `/var/lib/faasd/secrets/basic-auth-password` | gerenciada pelo faasd; o Makefile lê de lá |

Nunca versione `.env`, `infra/.gerado/` nem `terraform.tfvars` (todos no `.gitignore`).

## Backup e restauração

| Dado | Backup | Restauração |
|---|---|---|
| Avaliações e notificações (Scylla) | `dc exec scylla nodetool snapshot -t $(date +%F)` e copiar `/var/lib/scylla/data/alternator_*/*/snapshots/` do volume `feedback_scylla-data` | parar o Scylla, copiar os SSTables do snapshot para a pasta da tabela, `nodetool refresh` |
| Relatórios (MinIO) | `mc mirror local/feedback-relatorios /backup/relatorios` (com o usuário root) | `mc mirror /backup/relatorios local/feedback-relatorios` |
| Keycloak (usuários) | `dc exec postgres pg_dump -U keycloak keycloak > keycloak.sql` | `psql` no container novo |
| Configuração | o próprio git + `.env` (guarde fora da VM, num cofre) | `git clone` + `.env` + `make up` |

Os volumes nomeados (`feedback_*`) sobrevivem a `dc down`. Só `make destroy` (`down -v`) os apaga.

## Solução de problemas

| Sintoma | Causa provável | Como resolver |
|---|---|---|
| `401` mesmo com token novo | `iss` do token difere da chave do consumer no Kong. O Keycloak usa `KEYCLOAK_URL` como hostname fixo | decodifique o token (`cut -d. -f2 \| base64 -d`) e compare `iss` com `KEYCLOAK_URL/realms/feedback`. Se mudou o `.env`, rode `scripts/preparar.sh && dc up -d keycloak kong` |
| `403 SEM_PERMISSAO` | usuário sem a role `aluno` | Keycloak → realm feedback → Users → Role mapping |
| `429` logo nas primeiras chamadas | janela fixa de 1 min por IP já esgotada (por exemplo, pelo teste de rate limit) | aguarde a virada do minuto |
| `502`/`504` no Kong | gateway do faasd fora do ar ou função subindo (cold start) | `systemctl status faasd`; `faas-cli describe <função>`; repita após alguns segundos |
| Função não alcança Scylla, MinIO ou SMTP (timeout) | regra de FORWARD ausente (o Docker define a política como DROP) | `systemctl restart cloudlab-rede`; `sudo iptables -L DOCKER-USER -n` deve listar `openfaas0` |
| `faas-cli deploy` ok, mas a função não sobe (`pull access denied`) | registry privado sem credencial no faasd | `docker login`, depois `make deploy` (copia `~/.docker/config.json` para `/var/lib/faasd/.docker/`) ou torne o pacote público |
| Função cai no start com "Variável de ambiente obrigatória ausente" | env ou secret faltando | conferir `stack.yml` e `faas-cli secret list`; ver os logs da função |
| Alerta não chega ao administrador | evento não publicado, urgência ≠ CRITICA, ou nats-connector parado | logs `evento_nao_publicado`/`notificacao_*`; `sudo journalctl -t openfaas:nats-connector`; reprocessar |
| Relatório não saiu na segunda-feira | cron em UTC (`0 11 * * 1`) ou função falhando | `sudo journalctl -t openfaas:cron-connector`; invoque manualmente |
| `insufficient physical memory` no Scylla | limite do container menor que `--memory` + reserva | manter `mem_limit` ≥ `--memory` + `--reserve-memory` (1G + 256M < 1536m) |
| Keycloak demora a ficar healthy | primeiro boot faz o *build* do Quarkus e importa o realm | normal até cerca de 2 min (`start_period` 60 s + retries) |
| Dashboard sem dados de urgência | Alloy sem acesso ao journal ou função sem logs ainda | `dc logs alloy`; no Explore: `{job="faasd"}`; confirme que `/var/log/journal` existe na VM |
| Painéis de Prometheus vazios | o Grafana não alcança `127.0.0.1:9090` | `curl -s 127.0.0.1:9090/-/ready` na VM; o Grafana precisa estar com `network_mode: host` |
| Alteração no `realm-feedback.json` não teve efeito | a importação só acontece se o realm não existir | aplique pelo console, ou `make destroy && make up` (apaga os dados) |
