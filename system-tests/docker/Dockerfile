FROM ubuntu:16.04

LABEL Description="Java + Ubuntu (OpenJDK)"

ENV DEBIAN_FRONTEND noninteractive



CMD /bin/bash

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget curl iputils-ping dnsutils unzip software-properties-common
#install Gradle
RUN wget -q https://services.gradle.org/distributions/gradle-6.6.1-bin.zip \
    && unzip gradle-6.6.1-bin.zip -d /opt \
    && rm gradle-6.6.1-bin.zip

# Set Gradle in the environment variables
ENV GRADLE_HOME=/opt/gradle-6.6.1
ENV PATH=/opt/gradle-6.6.1/bin:$PATH
RUN add-apt-repository ppa:openjdk-r/ppa

RUN apt-get update
RUN apt install docker.io -y
RUN apt install openjdk-11-jdk -y
RUN docker --version

COPY . /src
WORKDIR /src
USER root
CMD /bin/bash
