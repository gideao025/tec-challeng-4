# 0010. Javalin + of-watchdog HTTP, sem Spring Boot

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

As funções escalam a zero, então o tempo de inicialização da JVM aparece direto na latência
da primeira chamada. A memória por função é limitada (256 a 320 MiB).

## Decisão

- Usar Javalin 6 como servidor embarcado, com uma rota por função, sem framework de injeção
  de dependências.
- Usar of-watchdog em modo `http`: o processo Java fica vivo entre invocações, e o watchdog
  repassa as requisições para `127.0.0.1:8082`.
- Montar as dependências à mão no `Main` (composition root) e manter os casos de uso como
  classes Java simples, testáveis sem servidor.
- Usar o cliente HTTP `url-connection-client` no AWS SDK, sem Netty nem Apache.

## Consequências

- A inicialização leva cerca de 1 a 2 s, contra 5 a 10 s de um Spring Boot equivalente.
- Não há auto-configuração: cada variável de ambiente é lida de forma explícita
  (`Config`), o que facilita a auditoria.
- `commons.Servidor` padroniza correlationId, `/health` e erro 500 para as três funções.
