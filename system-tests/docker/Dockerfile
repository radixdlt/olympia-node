FROM ubuntu:20.04

LABEL Description="Java + Ubuntu (OpenJDK)"

ENV DEBIAN_FRONTEND noninteractive



CMD /bin/bash

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget curl iputils-ping dnsutils unzip software-properties-common ca-certificates-java \
    iproute2 openjdk-11-jdk docker.io
#install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-6.6.1-bin.zip \
    && unzip gradle-6.6.1-bin.zip -d /opt \
    && rm gradle-6.6.1-bin.zip
#you might need this if using docker on windows
#RUN if uname -a | grep -q "microsoft"; then apt-get install iproute2 -y --no-install-recommends; fi;

# Set Gradle in the environment variables
ENV GRADLE_HOME=/opt/gradle-6.6.1
ENV PATH=/opt/gradle-6.6.1/bin:$PATH

RUN docker --version

COPY . /src
WORKDIR /src
USER root
CMD /bin/bash
