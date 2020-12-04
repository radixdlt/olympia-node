REGISTRY ?= eu.gcr.io/dev-container-repo

all:
    $(eval GIT_BRANCH=$(shell git rev-parse --abbrev-ref HEAD | sed 's/\//-/g'))
    $(eval GIT_COMMIT=$(shell git log -1 --format=%h ))
    TAG ?= $(GIT_BRANCH)-$(GIT_COMMIT)
    REPO ?= $(REGISTRY)/radixdlt-core

.PHONY: build
build:
	./gradlew deb4docker

.PHONY: package
package: build
	docker-compose -f docker/node-1.yml build
	docker tag radixdlt/radixdlt-core:develop $(REPO):$(TAG)

.PHONY: publish
publish: package
	docker push $(REPO):$(TAG)