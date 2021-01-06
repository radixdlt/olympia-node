REGISTRY ?= eu.gcr.io/dev-container-repo

all:
    $(eval GIT_BRANCH=$(shell git rev-parse --abbrev-ref HEAD | sed 's/\//-/g'))
    $(eval GIT_COMMIT=$(shell git log -1 --format=%h ))
    TAG ?= $(GIT_BRANCH)-$(GIT_COMMIT)
    CORE_REPO ?= $(REGISTRY)/radixdlt-core
    FAUCET_REPO ?= $(REGISTRY)/faucet

.PHONY: build
build-core:
	cd radixdlt && ../../gradlew deb4docker

build-faucet:
	cd faucet-service && ../../gradlew deb4docker

.PHONY: package
package: build-core build-faucet
	docker-compose -f docker/node-1.yml build
	docker tag radixdlt/radixdlt-core:develop $(CORE_REPO):$(TAG)
	docker tag radixdlt/faucet:develop $(FAUCET_REPO):$(TAG)

.PHONY: publish
publish: package
	docker push $(CORE_REPO):$(TAG)
	docker push $(FAUCET_REPO):$(TAG)

.PHONY: package-test
package-test:
	docker build -t radixcore-test:$(TAG) -f docker/Dockerfile.test .