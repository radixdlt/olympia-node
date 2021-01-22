#!/bin/bash

# Fail on error
set -e

# Where we are run from
scriptdir=$(dirname "$0")

while getopts fn:p: flag
do
  case "${flag}" in
    f) force_delete=true;;
    p) port_param=${OPTARG};;
    n) validators=${OPTARG};;
    *) echo "Invalid parameters … exiting"
       exit 1;;
  esac
done

# Being a bit strict. You need  port parameter and a node parameter to continue
# and they both have to be numbers.
re='^[0-9]+$'

if ! [[ $port_param =~ $re ]] || ! [[ $validators =~ $re ]]; then
  echo "Invalid parameter setting"
  exit 1
fi

network_name="network_${validators}_nodes"
file_name="${scriptdir}/../node-${validators}.yml"

# Added -f flag that will force the node file to be overwritten without checking it exists first.
if ! [ $force_delete ]; then

   if  [ -f "${file_name}" ]; then
    echo "File ${file_name} already exist, aborting."
    exit 1
  fi

fi



echo "version: '2.1'" >${file_name}
echo "services:" >>${file_name}

for ((i=0;i<$validators;i++));
do
    nodelist=''
    p0=$(printf "%02d" $(( 80+i )))
    p1=$(printf "%02d" $(( 11+i )))
    p2=$(printf "%02d" $(( 5+i )))

    for ((j=0;j<$validators;j++));
    do
        if [ $j -ne $i ]
        then
            nodelist="$nodelist,core$j"
        else
            if [ $validators -eq 1 ]
            then
                nodelist=",core0"
            fi
        fi
    done

    nodelist="${nodelist:1}"

    echo "  core${i}:">>${file_name}
    echo "    extends:">>${file_name}
    echo "      file: core.yml">>${file_name}
    echo "      service: core">>${file_name}
    echo "    environment:">>${file_name}
    echo "      RADIXDLT_HOST_IP_ADDRESS: core${i}">>${file_name}
    echo "      RADIXDLT_NETWORK_SEEDS_REMOTE: ${nodelist}">>${file_name}
    echo "      RADIXDLT_NODE_KEY: \${RADIXDLT_VALIDATOR_${i}_PRIVKEY:?err}">>${file_name}
    echo "    networks:">>${file_name}
    echo "      - $network_name">>${file_name}
    echo "    ports:">>${file_name}
    echo "      - \"${port_param}${p0}:8080\"">>${file_name}
    echo "      - \"90${p1}:9011\"">>${file_name}
    echo "      - \"505${p2}:50505\"">>${file_name}

    if [ $i -eq 0 ]
    then
        echo "      - \"10000:10000/tcp\"">>${file_name}
        echo "      - \"20000:20000/tcp\"">>${file_name}
        echo "      - \"30000:30000/tcp\"">>${file_name}
    fi
done

echo "  faucet:">>${file_name}
echo "    extends:">>${file_name}
echo "      file: faucet.yml">>${file_name}
echo "      service: faucet">>${file_name}
echo "    networks:">>${file_name}
echo "      - $network_name">>${file_name}
echo "networks:">>${file_name}
echo "  $network_name:">>${file_name}

