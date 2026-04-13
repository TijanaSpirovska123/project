#!/usr/bin/env bash
set -euo pipefail
cd /opt/project

git pull origin master
docker compose up -d --build
docker image prune -f
