#!/usr/bin/env bash
# Sprawdza warunki wstępne profilu dev, NIE wypisując wartości sekretów.
# Uruchamiać z katalogu głównego projektu.
set -uo pipefail
ok=1
[ -f .env ] || { echo "BRAK .env"; ok=0; }
[ -f secret.properties ] || { echo "BRAK secret.properties"; ok=0; }
if [ -f .env ]; then
  for k in REDIS_PASSWORD POSTGRES_PASSWORD MEILI_MASTER_KEY; do
    v=$(grep -E "^$k=" .env | head -1 | cut -d= -f2-)
    [ -n "$v" ] || { echo ".env: brak/pusty $k"; ok=0; }
  done
fi
if [ -f .env ] && [ -f secret.properties ]; then
  meili=$(grep -E '^MEILI_MASTER_KEY=' .env | head -1 | cut -d= -f2- | tr -d '\r"')
  api=$(grep -E '^gamehive\.search\.api-key=' secret.properties | head -1 | cut -d= -f2- | tr -d '\r"')
  if [ -z "$api" ]; then echo "secret.properties: brak gamehive.search.api-key"; ok=0;
  elif [ "$meili" != "$api" ]; then echo "MEILI_MASTER_KEY (.env) != gamehive.search.api-key (secret.properties)"; ok=0; fi
fi
docker info >/dev/null 2>&1 || { echo "Docker nie działa (uruchom Docker Desktop)"; ok=0; }
if command -v netstat >/dev/null 2>&1 && netstat -an 2>/dev/null | grep -qE '[:.]8080 .*LISTEN'; then
  echo "UWAGA: port 8080 już zajęty — aplikacja dev być może już działa"
fi
[ $ok = 1 ] && echo "preflight OK" || { echo "preflight NIE OK"; exit 1; }
