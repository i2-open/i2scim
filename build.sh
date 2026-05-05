#!/bin/bash
#
# Copyright 2021.  Independent Identity Incorporated
# Licensed under the Apache License, Version 2.0
#
# Build helper: builds the multi-arch i2scim-universal docker image.
# JVM compile/test/package now happen via plain `mvn install` from the repo root —
# this script only wraps `docker buildx build`.

set -euo pipefail

usage() {
  cat <<EOF
Usage: $0 [options]

Options:
  -t, --test        run maven tests (default skips them)
  -p, --push        multi-arch buildx + push to docker.io
      --tag TAG     image tag (default: latest)
  -b, --build       maven build only — skip docker step (back-compat no-op for the
                    docker step; mvn install is the build now)
  -h, --help        show this help
EOF
}

I2SCIM_ROOT=$(cd "$(dirname "$0")" && pwd)

skip_tests=true
tag="latest"
push=0
build_only=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    -t|--test)   skip_tests=false ;;
    -p|--push)   push=1 ;;
    -b|--build)  build_only=1 ;;
    --tag)       tag="$2"; shift ;;
    -h|--help)   usage; exit 0 ;;
    *)           usage; exit 1 ;;
  esac
  shift
done

echo "*************************************************"
echo "  i2scim build — tag=${tag} push=${push} skipTests=${skip_tests}"
echo "*************************************************"

# Maven build (root-level install — fixed in slice 6 to no longer require -N + per-module install)
mvn -f "${I2SCIM_ROOT}/pom.xml" clean install -DskipTests=${skip_tests}

if [[ ${build_only} -eq 1 ]]; then
  echo "Build only requested — skipping docker."
  exit 0
fi

GIT_COMMIT=$(git -C "${I2SCIM_ROOT}" rev-parse HEAD)
BUILD_DATE=$(date -u +%Y-%m-%dT%H:%M:%SZ)
VERSION=$(mvn -q -f "${I2SCIM_ROOT}/pom.xml" help:evaluate -Dexpression=project.version -DforceStdout)

cd "${I2SCIM_ROOT}/i2scim-server"

common_args=(
  -f src/main/docker/Dockerfile.jvm
  --attest type=sbom
  --attest type=provenance,mode=max
  --build-arg GIT_COMMIT="${GIT_COMMIT}"
  --build-arg BUILD_DATE="${BUILD_DATE}"
  --build-arg VERSION="${VERSION}"
  -t "independentid/i2scim-universal:${tag}"
)

if [[ ${push} -eq 1 ]]; then
  docker buildx build --platform linux/amd64,linux/arm64 --push "${common_args[@]}" .
else
  docker buildx build --load "${common_args[@]}" .
fi

echo "*************************************************"
echo "  COMPLETE: $(date +"%Y-%m-%d %H:%M:%S")"
echo "*************************************************"
