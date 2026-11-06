# Registros de Decisão de Arquitetura (ADR)

Cada ADR registra **uma** decisão: contexto, decisão, alternativas e consequências.
Formato: Michael Nygard, em português. ADRs não são editados depois de aceitos. Se a
decisão mudar, crie um novo ADR que **substitui** o anterior.

| # | Decisão | Status |
|---|---|---|
| [0001](0001-faasd-como-faas.md) | OpenFaaS faasd como plataforma FaaS | Aceito |
| [0002](0002-urgencia-derivada-e-persistida.md) | Urgência derivada da nota e persistida na gravação | Aceito |
| [0003](0003-publicacao-explicita-eventpublisher.md) | Publicação explícita via `EventPublisher` em vez de Streams | Aceito |
| [0004](0004-particao-diaria-fuso-brasilia.md) | Partição diária pelo fuso de Brasília | Aceito |
| [0005](0005-jetstream-ao-lado-do-nats-streaming.md) | NATS JetStream ao lado do NATS Streaming do faasd | Aceito |
| [0006](0006-scale-to-zero-com-idler-proprio.md) | Scale-to-zero com idler próprio | Aceito |
| [0007](0007-gateway-faasd-somente-loopback.md) | Gateway do faasd só em loopback; Kong na rede do host | Aceito |
| [0008](0008-imagem-minio-chainguard.md) | Imagem do MinIO da Chainguard fixada por digest | Aceito |
| [0009](0009-rota-sem-acento.md) | Rota `/avaliacao` sem acento | Aceito |
| [0010](0010-javalin-sem-spring.md) | Javalin + of-watchdog HTTP, sem Spring Boot | Aceito |

Modelo para um novo ADR: copie [template.md](template.md).
