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

function compile_module() {
  echo "\n\nCompiling ${1} at ${2} ..."
  cd $2
  mvn clean compile -DskipTests=$skip
  retVal=$?
  if [ $retVal -ne 0 ]
  then
    echo "Error performing maven packaging [${1}]: "+$retVal
    exit $retVal
  fi
}

function build_package() {
  echo "\n\nBuilding Packaging ${1} at ${2} ..."
  cd $2
  mvn clean install -DskipTests=$skip
  retVal=$?
  if [ $retVal -ne 0 ]
  then
    echo "Error performing build packaging for [${1}]: "+$retVal
    exit $retVal
  fi
}

function package_module() {
  echo "\n\nPackaging ${1} at ${2} ..."
  cd $2
  mvn package -DskipTests=$skip
  retVal=$?
  if [ $retVal -ne 0 ]
  then
    echo "Error performing maven packaging [${1}]: "+$retVal
    exit $retVal
  fi
}

cd ../
I2SCIM_ROOT=$(pwd)

echo "Current dir: ${I2SCIM_ROOT}"

skip=true
rtag="0.7.2-SNAPSHOT"
buildOnly=0
push=0

echo "*************************************************"
echo "  Starting i2scim-universal Build "

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

build_package "SCIM Universal" "${I2SCIM_ROOT}/i2scim-universal"

if [ $buildOnly -eq 1 ]
then
  show_complete
  exit 0
fi

echo ""
echo "\tBuilding Docker Image..."
echo ""

cd ${I2SCIM_ROOT}/i2scim-universal
if [ $push -eq 1 ]
then
  docker buildx build --platform linux/amd64,linux/arm64 -f src/main/docker/Dockerfile.jvm --push -t independentid/i2scim-universal:$rtag .
else
  docker buildx build --load -f src/main/docker/Dockerfile.jvm -t independentid/i2scim-universal:$rtag .
fi
retVal=$?
if [ $retVal -ne 0 ]
then
  echo "Docker error packaging i2scim-universal: "+$retVal
  exit $retVal
fi

show_complete