# 0007. Gateway do faasd só em loopback; Kong na rede do host

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

Requisito: "Kong valida o JWT antes de chegar na função; nenhuma função exposta
diretamente". No OpenFaaS CE, a rota `/function/*` do gateway não tem autenticação. Se a
porta 8080 ficar acessível na LAN, qualquer um contorna o Kong.

## Decisão

- Publicar o gateway do faasd somente em `127.0.0.1:8080`.
- Rodar o Kong (e o Grafana, que lê o Prometheus em `127.0.0.1:9090`) com
  `network_mode: host`.
- Alternator, API S3 e SMTP escutam somente no IP da `docker0` (`HOST_INTERNO`), que as
  funções alcançam pelo host e a LAN não.

## Alternativas consideradas

- **Segredo compartilhado** (header injetado pelo Kong e conferido pela função): é defesa
  em profundidade, mas depende de cada função implementar a checagem.
- **Firewall (ufw)**: funciona, mas as regras ficam fora do código do projeto e brigam com
  as regras de iptables do Docker.

## Consequências

- A única porta de entrada de funções é o Kong (`:8000`).
- `faas-cli` e `make deploy` precisam rodar na própria VM ou por túnel SSH
  (`ssh -L 8080:127.0.0.1:8080 cloudlab`).
