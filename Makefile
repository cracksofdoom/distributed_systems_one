.PHONY: help build test server client clean

GRADLE := ./gradlew
HOST ?= localhost
NAME ?= guest
PORT ?= 1099
SERVICE_PORT ?= 1100
GROUP ?= 234.20.7.1
MULTICAST_PORT ?= 6789
ADVERTISE_HOST ?=
ADVERTISE_ARG := $(if $(ADVERTISE_HOST),--advertise-host $(ADVERTISE_HOST),)

help:
	@echo 'make build | test | server | client | clean'
	@echo 'Options: HOST=localhost NAME=guest PORT=1099 SERVICE_PORT=1100 GROUP=234.20.7.1 MULTICAST_PORT=6789 ADVERTISE_HOST=IP'

build:
	$(GRADLE) build

test:
	$(GRADLE) test

server:
	$(GRADLE) runServer --args="--port $(PORT) --service-port $(SERVICE_PORT) --group $(GROUP) --multicast-port $(MULTICAST_PORT) $(ADVERTISE_ARG)"

client:
	$(GRADLE) run --args="--host $(HOST) --port $(PORT) --name $(NAME) --multicast-port $(MULTICAST_PORT)"

clean:
	$(GRADLE) clean
