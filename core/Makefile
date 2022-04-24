REGISTRY ?= eu.gcr.io/dev-container-repo

all:
    $(eval GIT_BRANCH=$(shell git rev-parse --abbrev-ref HEAD | sed 's/\//-/g'))
    $(eval GIT_COMMIT=$(shell git log -1 --format=%h ))
    TAG ?= $(GIT_BRANCH)-$(GIT_COMMIT)
    CORE_REPO ?= $(REGISTRY)/radixdlt-core

.PHONY: build
build-core:
	cd .. && ./gradlew deb4docker

.PHONY: package
package: build-core
	docker-compose -f docker/node-1.yml build
	docker tag radixdlt/radixdlt-core:main $(CORE_REPO):$(TAG)

.PHONY: publish
publish: package
	docker push $(CORE_REPO):$(TAG)
