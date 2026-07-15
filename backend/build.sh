#!/bin/sh
# Usage : ./build.sh <int|prod>
# Construit le jar Quarkus puis l'image Docker jrec/meteox-laws:<env>-<sha court>.
# Pattern espace-client (docker stack sur jrec.fr).
set -e
cd "$(dirname "$0")"
[ -n "$1" ] || { echo "Usage: ./build.sh <int|prod>" >&2; exit 1; }
. "./$1.env"

TAG="${MX_ENV}-$(git rev-parse --short HEAD)"

./mvnw -B -DskipTests package
docker build -t "ghcr.io/jrechet/meteox-laws:${TAG}" .

echo "Image construite : ghcr.io/jrechet/meteox-laws:${TAG}"
echo "Note : le chemin nominal de déploiement est la GitHub Action backend-cd.yml (push sur main)."
