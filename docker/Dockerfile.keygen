FROM eclipse-temurin:17-alpine
MAINTAINER radixdlt <devops@radixdlt.com>

RUN mkdir -p /keygen/bin keygen/lib

ADD bin/ /keygen/bin
ADD lib/ /keygen/lib
WORKDIR /keygen/

ENTRYPOINT ["bin/keygen"]