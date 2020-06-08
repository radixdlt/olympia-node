FROM gradle:jdk10
#RUN apk add --no-cache curl tar bash procps
COPY . /src
WORKDIR /src
USER root
RUN chown -R gradle /src
USER gradle

RUN gradle clean
ENTRYPOINT [ "gradle clean dockerSystemTests" ]