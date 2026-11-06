#!/bin/sh
# Provisiona o MinIO: bucket privado + políticas mínimas + usuários. Idempotente.
set -eu
mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null

mc mb --ignore-existing "local/$MINIO_BUCKET"
mc anonymous set none "local/$MINIO_BUCKET"   # bucket privado: sem acesso anônimo

# Função gerar-relatorio-semanal: só grava objetos sob relatorios/
cat > /tmp/relatorio-escrita.json <<JSON
{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Action":["s3:PutObject"],
 "Resource":["arn:aws:s3:::$MINIO_BUCKET/relatorios/*"]}]}
JSON
# Administradores: só leitura dos relatórios
cat > /tmp/relatorio-leitura.json <<JSON
{"Version":"2012-10-17","Statement":[
 {"Effect":"Allow","Action":["s3:ListBucket","s3:GetBucketLocation"],"Resource":["arn:aws:s3:::$MINIO_BUCKET"]},
 {"Effect":"Allow","Action":["s3:GetObject"],"Resource":["arn:aws:s3:::$MINIO_BUCKET/relatorios/*"]}]}
JSON
mc admin policy create local relatorio-escrita /tmp/relatorio-escrita.json
mc admin policy create local relatorio-leitura /tmp/relatorio-leitura.json

mc admin user add local "$MINIO_SVC_ACCESS_KEY" "$MINIO_SVC_SECRET_KEY"
mc admin user add local "$MINIO_ADMIN_USER" "$MINIO_ADMIN_PASSWORD"
mc admin policy attach local relatorio-escrita --user "$MINIO_SVC_ACCESS_KEY" 2>/dev/null || true
mc admin policy attach local relatorio-leitura --user "$MINIO_ADMIN_USER" 2>/dev/null || true
echo "MinIO provisionado: bucket $MINIO_BUCKET"
