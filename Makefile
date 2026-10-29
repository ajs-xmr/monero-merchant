.PHONY: install stagenet up down logs ps restart config pull build rebuild reset clean help
.DEFAULT_GOAL := help

ifneq ($(filter testnet,$(MAKECMDGOALS)),)
$(error Testnet is not supported. Use make install stagenet for testing)
endif

INSTALL_NETWORK := $(filter stagenet,$(MAKECMDGOALS))
ifneq ($(INSTALL_NETWORK),)
ifeq ($(filter install,$(MAKECMDGOALS)),)
$(error Use make install stagenet)
endif
endif

install:
	./setup.sh $(INSTALL_NETWORK)
	docker compose up -d --build

stagenet:
	@:

up:
	docker compose up -d

build:
	docker build -t moneromerchant/monero-merchant-backend ./backend
	docker build -t monero-merchant-frontend ./frontend
	docker build -t monero-merchant-gateway ./gateway

rebuild:
	$(MAKE) build
	docker compose up -d --force-recreate backend frontend gateway

down:
	docker compose down

logs:
	docker compose logs -f

ps:
	docker compose ps

restart:
	docker compose down
	docker compose up -d

config:
	docker compose config

pull:
	docker compose pull --ignore-buildable

reset:
	docker compose down -v --remove-orphans
	rm -f .env
	sudo rm -rf moneropay
	sudo rm -rf simple-monero-wallet-rpc-docker

clean:
	docker compose down --remove-orphans
	docker builder prune -f

help:
	@echo "Available targets:"
	@echo "  make install   - run setup.sh, build and start services"
	@echo "  make install stagenet - install using the Monero stagenet"
	@echo "  make up        - start existing containers"
	@echo "  make build     - build the backend, frontend and gateway images from local source"
	@echo "  make rebuild   - build local source and recreate the backend, frontend and gateway"
	@echo "  make down      - stop containers"
	@echo "  make logs      - follow container logs"
	@echo "  make ps        - show container status"
	@echo "  make restart   - restart containers"
	@echo "  make config    - show resolved docker compose config"
	@echo "  make pull      - pull remote images"
	@echo "  make reset     - DELETE containers, database/wallet data, .env, and cloned deps"
	@echo "  make clean     - remove containers and prune builder cache; preserve application data"
