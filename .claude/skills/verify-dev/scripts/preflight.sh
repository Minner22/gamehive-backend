#!/usr/bin/env bash
# Sprawdza warunki wstępne profilu dev, NIE wypisując wartości sekretów.
# Uruchamiać z katalogu głównego projektu.
# GH-159: jedynym plikiem z sekretami jest .env (czytają go docker compose i Spring: spring.config.import).
set -uo pipefail
ok=1
REQUIRED="REDIS_PASSWORD POSTGRES_PASSWORD MEILI_MASTER_KEY JWT_ACTIVATION_SECRET JWT_REFRESH_SECRET JWT_ACCESS_SECRET JWT_PASSWORDRESET_SECRET MAIL_USERNAME MAIL_PASSWORD"
if [ -f .env ]; then
  for k in $REQUIRED; do
    v=$(grep -E "^$k=" .env | head -1 | cut -d= -f2- | tr -d '\r')
    if [ -z "$v" ]; then echo ".env: brak/pusty $k"; ok=0;
    # parser properties (Spring) NIE zdejmuje cudzysłowów, compose tak — ta sama wartość różniłaby się w obu miejscach
    elif [[ "$v" == \"* || "$v" == \'* ]]; then echo ".env: $k w cudzysłowie — usuń cudzysłowy"; ok=0; fi
  done
else
  echo "BRAK .env"; ok=0
fi
if [ -f secret.properties ] && grep -qE '^(jwt\.|spring\.mail\.|gamehive\.search\.api-key)' secret.properties; then
  echo "UWAGA: secret.properties nie jest już czytany (GH-159) — sekrety aplikacji przenieś do .env i usuń plik"
fi
docker info >/dev/null 2>&1 || { echo "Docker nie działa (uruchom Docker Desktop)"; ok=0; }
if command -v netstat >/dev/null 2>&1 && netstat -an 2>/dev/null | grep -qE '[:.]8080 .*LISTEN'; then
  echo "UWAGA: port 8080 już zajęty — aplikacja dev być może już działa"
fi
[ $ok = 1 ] && echo "preflight OK" || { echo "preflight NIE OK"; exit 1; }
