# 0001. OpenFaaS faasd como plataforma FaaS

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

O desafio exige uma arquitetura serverless. O ambiente autorizado é uma única VM de
4 vCPU / 6 GB num homelab Proxmox, que também precisa hospedar IdP, gateway, banco,
storage e observabilidade.

## Decisão

Usar **OpenFaaS faasd** (containerd + CNI, sem Kubernetes) com funções empacotadas como
imagens OCI e o **of-watchdog em modo HTTP**.

## Alternativas consideradas

- **OpenFaaS/Knative/OpenWhisk sobre Kubernetes (k3s)**: mais próximo de produção, mas o
  plano de controle consome 1 a 2 GB e disputa memória com ScyllaDB e Keycloak.
- **Fn Project** (base do OCI Functions): comunidade menor e menos connectors prontos.
- **Containers comuns sem FaaS**: não atende ao requisito serverless (sem scale-to-zero,
  sem invocação por evento).

## Consequências

- O consumo é pequeno (cerca de 300 MB para os serviços centrais) e a plataforma traz
  Prometheus, NATS e connectors (cron, NATS).
- As imagens são portáveis para Lambda (container image), Azure Functions e OCI Functions.
- A edição CE tem limitações: NATS Streaming legado (ver [0005](0005-jetstream-ao-lado-do-nats-streaming.md)),
  nenhum idler (ver [0006](0006-scale-to-zero-com-idler-proprio.md)) e licença de uso
  não comercial, adequada ao contexto acadêmico.
