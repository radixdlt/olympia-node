# Running local Radix networks with docker

## Setup

1. Install [Docker for Mac](https://docs.docker.com/docker-for-mac/install/) or [Docker for Linux](https://docs.docker.com/engine/install/ubuntu/) version `20.10.10` or newer
2. Be happy :)

## File structure

A highly configurable Radix client image is implemented in [Dockerfile](Dockerfile).
Configuration is done by means of environment variables passed to Docker containers at startup.

Different network setups are implemented in [docker-compose](https://docs.docker.com/compose/) files. (*.yml).

Ready to use configuration files are provided for 1-5 nodes: [1](node-1.yml), [2](node-2.yml), 
[3](node-3.yml), [4](node-4.yml) and [5](node-5.yml) nodes. If configuration with other number of nodes is necessary, 
it can be generated using [configuration generator](scripts/generate-yml.sh). 

Description below refers single node configuration. In order to run other configurations just replace ```docker/node-1.yml``` 
with relative path to necessary configuration file.

## Generating New Configuration
In order to generate `.yml` file for necessary number of nodes, just run following command 
from `docker/script` subdirectory:
```shell
$ ./generate-yml.sh <number of nodes>
```
Script will generate configuration file named `node-<number of nodes>.yml` in the same directory 
where other `node-X.yml` files reside.

## Build the radixdlt debian package

The [Dockerfile](Dockerfile) depends on the `radixdlt_*_all.deb` package which is built with:

```shell
$ ./gradlew deb4docker
```

## Create and start the single node network

```shell
$ docker-compose -f docker/node-1.yml up -d --build
```

To see the individual `radixdlt` containers:

```shell
$ docker ps
```

## Destroy the network

```shell
$ docker-compose -f docker/node-1.yml down
```

## Follow combined logs

```shell
docker-compose -f docker/node-1.yml logs -f
```

## See the container metrics

```shell
$ docker stats docker_explorer_1
```

## Start an interactive shell session in a container

```shell
$ docker exec -it docker_explorer_1 bash
```

## Make an API call

```shell
$ docker exec docker_explorer_1 curl -s http://explorer:8080/api/system
```

## Start spamathon

The API port is not exported by default, so we go through one of the containers:

```shell
$ docker exec docker_explorer_1 curl -s 'http://validator:8080/api/atoms/spamathon?iterations=100000&rate=1000'
```

## Debugging, profiling, etc.

[VisualVM](https://visualvm.github.io/) is your friend.

The `JMX` ports are exposed to ports `9010-90xx` on the docker host. Use `docker ps` to find the specific JMX port you want to connect VisualVM to.
(The JMX host is thus `localhost`).


Heapdumps don't work from VisualVM currently - the alternate is to use jmap, for example:

```shell
$ docker exec docker_explorer_1 jmap -dump:live,format=b,file=/tmp/radixdlt.hprof 1
$ docker cp docker_explorer_1:/tmp/radixdlt.hprof .
```

# Running from Jenkins

Ephemeral networks are supported by using the `-p` argument with `docker-compose`.

## Build the radixdlt debian package

The [Dockerfile](Dockerfile) depends on the `radixdlt_*_all.deb` package which is built with:

```shell
$ ./gradlew deb4docker
```

## Create and start the network

Create an ephemeral network called `test$BUILD_NUMBER`, in example `test123`.

```shell
$ docker-compose -p test$BUILD_NUMBER -f docker/jenkins-network.yml up -d --build
```

# Find the IP number of the core0 node

```shell
$ docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' test${BUILD_NUMBER}_core0_1
```
# Find the IP of all core nodes

```shell
$ for i in 0 1 2 3 4 5; do docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' test${BUILD_NUMBER}_core${i}_1; done
```

# kill the network

```shell
$ docker-compose -p  test$BUILD_NUMBER -f docker/node-1.yml down
```