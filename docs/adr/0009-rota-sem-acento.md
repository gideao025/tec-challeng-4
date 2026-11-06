# 0009. Rota `/avaliacao` sem acento

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

O enunciado grafa a rota como `/avaliação`. Em HTTP, o path precisa ser *percent-encoded*
(`/avalia%C3%A7%C3%A3o`), e clientes, proxies, logs e ferramentas de teste divergem quanto a
normalização e a exibição desse valor.

## Decisão

Expor **`POST /avaliacao`** (somente ASCII) e documentar a diferença no README e no OpenAPI.

## Consequências

- Sem ambiguidade de encoding no Kong, no Postman e nos logs.
- Se a banca exigir a grafia original, basta acrescentar o path `/avaliação` à mesma rota no
  `kong.yaml`, sem mudar o código.
