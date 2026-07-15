#!/bin/sh
# Usage : ./deploy.sh <int|prod>
# Déploie la stack docker swarm (jrec.fr) avec l'image taguée au SHA git court.
# Prérequis : ./build.sh <env> exécuté au même SHA, et MX_GITHUB_TOKEN exporté
# (token GitHub pour la création d'issues par le job check-sources).
set -e
cd "$(dirname "$0")"
[ -n "$1" ] || { echo "Usage: ./deploy.sh <int|prod>" >&2; exit 1; }
. "./$1.env"

export MX_IMAGE=ghcr.io/jrechet/meteox-laws
export MX_TAG="${MX_ENV}-$(git rev-parse --short HEAD)"

docker stack deploy -c docker-compose.yml "${MX_STACK}"

echo "Stack ${MX_STACK} déployée avec ${MX_IMAGE}:${MX_TAG} (https://${MX_DOMAIN}/api/health)"
