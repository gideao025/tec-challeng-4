# notificar-critico

Alerta os administradores, **uma única vez**, sobre avaliações `CRITICA`.

| | |
|---|---|
| Gatilho | subject NATS `avaliacao.criada` → nats-connector → `POST /function/notificar-critico` (anotação `topic`) |
| Classes | `Main` · `NotificarCritico` (caso de uso) · `Deduplicador` (tabela `notificacoes`) |
| Entrada | CloudEvent JSON ([asyncapi.yaml](../docs/api/asyncapi.yaml)) |
| Saída | `200 {"resultado": "NOTIFICADO" \| "IGNORADO" \| "DUPLICADO"}` · `400 EVENTO_INVALIDO` · e-mail |
| Env | `ALTERNATOR_ENDPOINT`, `TABELA_NOTIFICACOES` (`notificacoes`), `SMTP_HOST`, `SMTP_PORT`, `EMAIL_REMETENTE`, `EMAIL_ADMINS` (lista por vírgula) |
| Secrets | `alternator-access-key`, `alternator-secret-key` |

## Regras

- Ignora eventos de outro `type` e urgências diferentes de `CRITICA`.
- **Idempotência**: `PutItem` com `attribute_not_exists(id)` em `notificacoes`. Só quem
  consegue gravar envia o e-mail. Isso exige o Alternator com
  `--alternator-write-isolation=only_rmw_uses_lwt` (configurado no compose).
- Se o SMTP falhar, a reserva é desfeita (`liberar`) e a exceção vira 500: uma reentrega
  posterior consegue enviar.
- O e-mail contém **descrição, urgência e data de envio em horário de Brasília**, além de
  nota e id ([exemplo](../docs/exemplos/email-alerta-critico.txt)).

## Rodar localmente

```bash
./mvnw -q -pl notificar-critico -am package -DskipTests
ALTERNATOR_ENDPOINT=http://172.17.0.1:8200 ALTERNATOR_ACCESS_KEY=x ALTERNATOR_SECRET_KEY=y \
SMTP_HOST=172.17.0.1 EMAIL_REMETENTE=feedback@cloudlab.local EMAIL_ADMINS=coordenacao@fiap.local \
java -jar notificar-critico/target/app.jar

curl -X POST localhost:8082/ -d '{"specversion":"1.0","id":"e1","type":"avaliacao.criada",
  "data":{"id":"a1","descricao":"Sem professor","nota":1,"urgencia":"CRITICA","dataEnvio":"2026-11-05T13:00:00Z"}}'
# repita: a segunda chamada responde DUPLICADO. E-mail em http://localhost:8025
```

## Testes

`NotificarCriticoTest`: só CRITICA dispara, três campos obrigatórios no e-mail,
idempotência, liberação após falha de SMTP, evento malformado.
