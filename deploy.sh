#!/usr/bin/env bash
set -euo pipefail
cd /opt/project

git pull origin master
docker compose up -d --build
docker compose exec -T nginx nginx -s reload || docker compose restart nginx
docker image prune -f
