#!/usr/bin/env bash
#
# Copyright 2021.  Independent Identity Incorporated
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
#
# Remove Docker leftovers from i2scim *test* runs WITHOUT touching unrelated stacks.
#
# i2scim tests start mongo two ways, and both label everything they create with
# `org.testcontainers=true`:
#   * Testcontainers  -- the signals durability/store tests (one shared mongo:8.0).
#   * Quarkus MongoDB Dev Services -- the MongoProvider-backed tests (mongo:8.0).
# The Testcontainers ryuk reaper normally removes them on JVM exit. This script is a
# safety net for runs that were killed (kill -9, IDE "stop", CI timeout) before ryuk
# fired, leaving a mongo or ryuk container behind.
#
# It targets ONLY testcontainers-labeled containers/networks/volumes plus dangling
# (untagged) images. Named docker-compose stacks (i2gosignals, openid-conformance,
# your mongo replica set, scim_cluster*, keycloak-signals, grafana/loki/...) and any
# tagged image are deliberately left untouched -- they are not labeled by Testcontainers.
#
set -euo pipefail

LABEL="org.testcontainers=true"

echo "==> Removing Testcontainers-labeled containers (mongo test instances, ryuk)…"
ids=$(docker ps -aq --filter "label=${LABEL}" || true)
if [ -n "${ids}" ]; then
  # -v also drops the anonymous volumes those containers created.
  docker rm -fv ${ids}
else
  echo "    (none)"
fi

echo "==> Removing Testcontainers-labeled networks…"
nets=$(docker network ls -q --filter "label=${LABEL}" || true)
if [ -n "${nets}" ]; then
  docker network rm ${nets} || true
else
  echo "    (none)"
fi

echo "==> Removing Testcontainers-labeled volumes…"
vols=$(docker volume ls -q --filter "label=${LABEL}" || true)
if [ -n "${vols}" ]; then
  docker volume rm ${vols} || true
else
  echo "    (none)"
fi

echo "==> Pruning dangling (untagged) images…"
docker image prune -f

echo
echo "Done. Tagged images and unrelated compose stacks were left untouched."
echo
echo "Old mongo image *tags* from past test runs (mongo:6.0.x, mongo:7.x, etc.) are NOT"
echo "removed automatically because other projects may share them. To reclaim that space,"
echo "review and delete deliberately, e.g.:"
echo "    docker images mongo"
echo "    docker rmi mongo:6.0.3 mongo:6.0.4 mongo:6.0.13 mongo:7 mongo:7.0   # only the ones you don't want"
