# Plataforma de feedback de aulas - FIAP Tech Challenge Fase 4
# Alvos principais: make up | make deploy | make logs | make destroy
SHELL     := /bin/bash
COMPOSE   := docker compose --env-file .env -f infra/docker-compose.yaml
FUNCOES   := receber-avaliacao notificar-critico gerar-relatorio-semanal
CARREGA_ENV := set -a; . ./.env; set +a;
GATEWAY   ?= http://127.0.0.1:8080
FAASD_DIR := /var/lib/faasd

.PHONY: help provisionar up faasd deploy segredos build push logs destroy destroy-vm verify

help: ## lista os alvos
	@grep -E '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-12s %s\n", $$1, $$2}'

provisionar: ## cria a VM cloudlab no Proxmox (terraform apply)
	cd infra/terraform && terraform init -upgrade && terraform apply

up: ## sobe TODO o ambiente: .env/chaves, serviços de apoio, MinIO e faasd (overlay)
	scripts/preparar.sh
	$(COMPOSE) up -d --wait
	$(COMPOSE) run --rm minio-init
	@if [ -d $(FAASD_DIR) ]; then $(MAKE) --no-print-directory faasd; else echo "faasd não encontrado: pulei o overlay (ambiente local?)"; fi
	@echo "Ambiente no ar. Próximo passo: make deploy"

faasd: ## instala o overlay do faasd (JetStream + connectors) e o idler de scale-to-zero
	sudo install -d -o 65534 -g 65534 $(FAASD_DIR)/nats-js
	sudo install -m 644 infra/faasd/docker-compose.yaml $(FAASD_DIR)/docker-compose.yaml
	sudo install -m 755 infra/faasd/idler.sh /usr/local/bin/faasd-idler
	sudo install -m 644 infra/faasd/faasd-idler.service infra/faasd/faasd-idler.timer /etc/systemd/system/
	sudo systemctl daemon-reload
	sudo systemctl restart faasd faasd-provider
	sudo systemctl enable --now faasd-idler.timer

build: ## compila (testes inclusos) e gera as imagens das funções
	./mvnw -q verify
	@$(CARREGA_ENV) for f in $(FUNCOES); do \
	  docker build --build-arg MODULO=$$f -t $$REGISTRY/$$f:$$TAG . || exit 1; done

push: build
	@$(CARREGA_ENV) for f in $(FUNCOES); do docker push $$REGISTRY/$$f:$$TAG || exit 1; done

segredos: ## cria/atualiza os secrets do OpenFaaS a partir do .env
	@$(CARREGA_ENV) export OPENFAAS_URL=$(GATEWAY); \
	criar() { faas-cli secret create $$1 --from-literal="$$2" >/dev/null 2>&1 || faas-cli secret update $$1 --from-literal="$$2" >/dev/null; echo "secret $$1 ok"; }; \
	criar alternator-access-key "$$ALTERNATOR_ACCESS_KEY"; \
	criar alternator-secret-key "$$ALTERNATOR_SECRET_KEY"; \
	criar minio-access-key "$$MINIO_SVC_ACCESS_KEY"; \
	criar minio-secret-key "$$MINIO_SVC_SECRET_KEY"

deploy: push ## build + push das imagens, secrets e faas-cli deploy (rodar na cloudlab)
	@# faasd usa as credenciais de registry do docker login para baixar imagens privadas
	@if [ -f $$HOME/.docker/config.json ]; then sudo install -D -m 600 $$HOME/.docker/config.json $(FAASD_DIR)/.docker/config.json; fi
	sudo cat $(FAASD_DIR)/secrets/basic-auth-password | faas-cli login -g $(GATEWAY) -s
	$(MAKE) --no-print-directory segredos
	@$(CARREGA_ENV) faas-cli deploy -f stack.yml -g $(GATEWAY)

logs: ## logs: make logs F=<função> | S=<serviço do compose>
	@if [ -n "$(F)" ]; then faas-cli logs $(F) -g $(GATEWAY) --tail; \
	elif [ -n "$(S)" ]; then $(COMPOSE) logs -f $(S); \
	else $(COMPOSE) logs -f --tail=50; fi

destroy: ## remove funções e serviços de apoio, INCLUINDO volumes (dados)
	-@$(CARREGA_ENV) faas-cli remove -f stack.yml -g $(GATEWAY)
	$(COMPOSE) --profile init down -v --remove-orphans

destroy-vm: ## destrói a VM cloudlab (terraform destroy)
	cd infra/terraform && terraform destroy

verify: ## validação de cada fase: testes + docker compose config
	scripts/preparar.sh >/dev/null
	./mvnw -q verify
	$(COMPOSE) config -q && echo "docker compose config: ok"
