#!/usr/bin/env bash
# Prepara arquivos locais antes do `docker compose up`:
#   1. cria .env a partir de .env.example com segredos aleatórios;
#   2. gera a chave RSA do realm Keycloak (privada -> .env, pública -> Kong);
#   3. renderiza infra/kong.yaml em infra/.gerado/kong.yaml.
# Idempotente: nada é sobrescrito se já existir.
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  while IFS= read -r linha; do
    if [[ "$linha" == *=trocar ]]; then
      echo "${linha%=trocar}=$(openssl rand -hex 16)"
    else
      echo "$linha"
    fi
  done < .env.example > .env
  chmod 600 .env
  echo ".env criado com segredos aleatórios"
fi

mkdir -p infra/.gerado
if [ ! -f infra/.gerado/jwt-privada.pem ]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/.gerado/jwt-privada.pem 2>/dev/null
  chmod 600 infra/.gerado/jwt-privada.pem
fi
openssl pkey -in infra/.gerado/jwt-privada.pem -pubout -out infra/.gerado/jwt-publica.pem

# Keycloak aceita a chave privada como base64 do PKCS#8 em uma única linha.
privada="$(grep -v -- '-----' infra/.gerado/jwt-privada.pem | tr -d '\n')"
if grep -q '^KC_RSA_PRIVATE_KEY=$' .env; then
  sed -i "s|^KC_RSA_PRIVATE_KEY=$|KC_RSA_PRIVATE_KEY=${privada}|" .env
fi

set -a; . ./.env; set +a
export KEYCLOAK_ISSUER="${KEYCLOAK_URL}/realms/feedback"
JWT_PUBLIC_KEY="$(sed 's/^/          /' infra/.gerado/jwt-publica.pem)"
export JWT_PUBLIC_KEY
perl -pe 's/\$\{(\w+)\}/exists $ENV{$1} ? $ENV{$1} : die "variável $1 não definida\n"/ge' \
  infra/kong.yaml > infra/.gerado/kong.yaml
echo "infra/.gerado/kong.yaml renderizado (issuer ${KEYCLOAK_ISSUER})"
