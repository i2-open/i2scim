#!/bin/bash
#
# Copyright 2021.  Independent Identity Incorporated
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# This script runs the maven build and builds the docker packages

function show_usage (){
    printf "Usage: $0 [options [parameters]]\n"
    printf "\n"
    printf "Options:\n"
    printf " -t|--test, run maven tests\n"
    printf " --tag [tag-version], Specify tag number\n"
    printf " -p|--push, push to docker"
    printf " -b|--build, maven build only"
    printf " -h|--help, Print help\n"

return 0
}

function show_complete () {
    echo "*************************************************"
    echo "  COMPLETE: "$(date +"%Y-%m-%d %H:%M:%S")
    echo "*************************************************"
    return 0
}

skip=true
rtag="0.6.1"
buildOnly=0
push=0

echo "*************************************************"
echo "  Starting i2scim Build "

while [ ! -z "$1" ]; do
  case "$1" in
     --push|-p)
         shift
         echo "\tPush requested"
         push=1
         ;;
     --test|-t)
         shift
         echo "\tTests requested"
         skip=false
         ;;
     --build|-b)
         shift
         echo "\tSkipping Docker build"
         buildOnly=1
         ;;
     --tag)
         shift
         rtag=$1
         ;;
     *)
        show_usage
        ;;
  esac
shift
done

echo "\tTag: $rtag"
echo "\tStarting: "$(date +"%Y-%m-%d %H:%M:%S")
echo "*************************************************"

echo "\n\tStarting maven packaging..."

mvn clean package -DskipTests=$skip
retVal=$?
if [ $retVal -ne 0 ]
then
  echo "Error performing maven packaging i2scim: "+$retVal
  exit $retVal
fi

if [ $buildOnly -eq 1 ]
then
  show_complete
  exit 0
fi

echo ""
echo "\tStarting Docker build i2scim-mem..."
echo ""

cd ./pkg-i2scim-prov-memory
if [ $push -eq 1 ]
then
  docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile.jvm --push -t independentid/i2scim-mem:$rtag .
else
  docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile.jvm -t independentid/i2scim-mem:$rtag .
fi
retVal=$?
if [ $retVal -ne 0 ]
then
  echo "Docker error packaging i2scim-mem: "+$retVal
  exit $retVal
fi
#cp target/kubernetes/kubernetes.yml ./4-i2scim-memory-deploy.yml

echo ""
echo "\tStarting Docker build i2scim-mongo..."
echo ""

cd ../pkg-i2scim-prov-mongodb
if [ $push -eq 1 ]
then
  docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile.jvm --push -t independentid/i2scim-mongo:$rtag .
else
  docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile.jvm -t independentid/i2scim-mongo:$rtag .
fi
retVal=$?
if [ $retVal -ne 0 ]
then
  echo "Docker error packaging i2scim-mongo: "+$retVal
  exit $retVal
fi
#cp target/kubernetes/kubernetes.yml ./4-i2scim-mongo-deploy.yml

show_complete