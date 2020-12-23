REGISTRY_PREFIX ?= eu.gcr.io/lunar-arc-236318/

all:
    $(eval GIT_BRANCH=$(shell git rev-parse --abbrev-ref HEAD | sed 's/\//-/g'))
    $(eval GIT_COMMIT=$(shell git log -1 --format=%h ))
    TAG ?= $(GIT_BRANCH)-$(GIT_COMMIT)

.PHONY: faucet
faucet:
	../gradlew clean distTar
	docker build -t $(REGISTRY_PREFIX)faucet-service:$(TAG) -f ./Dockerfile.alpine .

.PHONY: faucet-push
faucet-push: faucet
	docker push $(REGISTRY_PREFIX)faucet-service:$(TAG)
