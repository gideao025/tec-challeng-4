#!/usr/bin/env bash
# Prepara a VM cloudlab (Ubuntu 24.04): Docker Engine + faasd + faas-cli.
# Executado pelo cloud-init no primeiro boot; idempotente, pode ser reexecutado à mão.
#
# Docker e faasd compartilham o MESMO containerd (pacote containerd.io do Docker),
# isolados por namespace (moby x openfaas/openfaas-fn). Por isso NÃO usamos o
# hack/install.sh do faasd: ele instalaria um segundo containerd e o pacote runc,
# que conflita com containerd.io e removeria o Docker.
set -euo pipefail

FAASD_VERSION="0.19.7"
CNI_VERSION="v0.9.1"
USUARIO="${1:-fiap}"
export HOME="${HOME:-/root}"   # cloud-init roda sem HOME

# 1. Docker Engine (repositório oficial)
if ! command -v docker >/dev/null; then
  curl -fsSL https://get.docker.com | sh
fi
usermod -aG docker "$USUARIO" || true

# 2. Encaminhamento IP (exigido pela rede CNI do faasd)
echo "net.ipv4.conf.all.forwarding=1" > /etc/sysctl.d/90-faasd.conf
sysctl --system >/dev/null

# 3. CNI plugins + faas-cli via arkade
if ! command -v arkade >/dev/null; then
  curl -sLS https://get.arkade.dev | sh
fi
arkade system install cni --version "$CNI_VERSION" --path /opt/cni/bin --progress=false
arkade get faas-cli --progress=false
install -m 755 "$HOME/.arkade/bin/faas-cli" /usr/local/bin/faas-cli

# 4. faasd (binário + arquivos da versão fixada)
if [ ! -x /usr/local/bin/faasd ] || ! /usr/local/bin/faasd version 2>/dev/null | grep -q "$FAASD_VERSION"; then
  tmp="$(mktemp -d)"; mkdir -p "$tmp/hack"; cd "$tmp"
  curl -fSLs "https://github.com/openfaas/faasd/releases/download/${FAASD_VERSION}/faasd" -o /usr/local/bin/faasd
  chmod +x /usr/local/bin/faasd
  for f in docker-compose.yaml prometheus.yml resolv.conf hack/faasd-provider.service hack/faasd.service; do
    curl -fSLs "https://raw.githubusercontent.com/openfaas/faasd/${FAASD_VERSION}/${f}" -o "$f"
  done
  /usr/local/bin/faasd install
fi

# 5. Docker define a política FORWARD como DROP; libera o tráfego da bridge do faasd
#    (openfaas0) para que as funções alcancem a internet e os serviços do compose.
cat > /etc/systemd/system/cloudlab-rede.service <<'EOF'
[Unit]
Description=Libera FORWARD da bridge openfaas0 (faasd) frente às regras do Docker
After=docker.service faasd.service
Requires=docker.service

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/bin/sh -c 'iptables -C DOCKER-USER -i openfaas0 -j ACCEPT 2>/dev/null || iptables -I DOCKER-USER -i openfaas0 -j ACCEPT'
ExecStart=/bin/sh -c 'iptables -C DOCKER-USER -o openfaas0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT 2>/dev/null || iptables -I DOCKER-USER -o openfaas0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT'

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now cloudlab-rede.service

echo "cloudlab pronta: faasd $(faasd version 2>/dev/null | head -1), $(docker --version)"
