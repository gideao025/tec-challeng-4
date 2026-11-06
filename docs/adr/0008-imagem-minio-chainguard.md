# 0008. Imagem do MinIO da Chainguard fixada por digest

- **Status**: Aceito
- **Data**: 2026-11-05

## Contexto

Em 2025, a MinIO parou de publicar as imagens `minio/minio` e `minio/mc` no Docker Hub e no
quay.io (o pull retorna *access denied*). O binário continua AGPL e é compilado pela
comunidade.

## Decisão

Usar `cgr.dev/chainguard/minio:latest-dev` **fixada por digest SHA-256**, tanto para o
servidor quanto para o `minio-init` (a variante `-dev` inclui shell, `wget` e `mc`).

## Consequências

- O build é reproduzível: o digest não muda, mesmo que `latest-dev` mude.
- O console comunitário atual tem só o *object browser*. Os administradores usam esse
  console para baixar relatórios; usuários e políticas são criados pelo `mc` no init.
- Para atualizar: buscar o novo digest (`docker pull` + `docker inspect`) e trocar nos
  dois serviços do compose.
