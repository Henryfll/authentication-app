#!/usr/bin/env bash
# Prueba de humo del despliegue: levanta compose.prod.yaml con claves y contraseñas efímeras y
# comprueba que la imagen arranca con el perfil `prod` y se comporta como se espera.
#
#   docker build -t authentication-app:latest .
#   scripts/smoke-test.sh                     # usa IMAGE=authentication-app:latest
#   IMAGE=authentication-app:ci scripts/smoke-test.sh
#
# Requisitos: docker (con compose), openssl, curl y python3. No toca tu `.env` ni tus volúmenes:
# usa un proyecto de compose propio que se elimina al terminar, con sus datos.
set -euo pipefail

cd "$(dirname "$0")/.."

export IMAGE="${IMAGE:-authentication-app:latest}"
export HTTP_PORT="${SMOKE_HTTP_PORT:-18080}"
PROJECT="authsmoke$$"
BASE="http://localhost:${HTTP_PORT}"
WORK="$(mktemp -d)"
FAILURES=0

# Valores efímeros que solo existen durante la prueba.
export POSTGRES_PASSWORD="$(openssl rand -hex 16)"
export JWT_ISSUER="https://auth.smoke.test"
export JWT_KEY_ID="smoke-key-1"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$WORK/jwt.pem" 2>/dev/null
JWT_PRIVATE_KEY="$(awk 'NF {printf "%s\\n", $0}' "$WORK/jwt.pem")"
export JWT_PRIVATE_KEY
ADMIN_EMAIL="smoke-admin@example.com"
ADMIN_PASSWORD="smoke $(openssl rand -hex 8) passphrase"
export AUTH_BOOTSTRAP_ADMIN_EMAIL="$ADMIN_EMAIL"
export AUTH_BOOTSTRAP_ADMIN_PASSWORD="$ADMIN_PASSWORD"
export OPENAPI_ENABLED=false

compose() { docker compose -p "$PROJECT" -f compose.prod.yaml "$@"; }

cleanup() {
  if [ "$FAILURES" -ne 0 ]; then
    echo "--- logs de la aplicación (últimas líneas) ---"
    compose logs --no-color --tail 25 app 2>/dev/null || true
  fi
  compose down -v --remove-orphans >/dev/null 2>&1 || true
  rm -rf "$WORK"
}
trap cleanup EXIT

pass() { echo "  ✓ $1"; }
fail() { echo "  ✗ $1"; FAILURES=$((FAILURES + 1)); }

# check "descripción" valor_esperado valor_obtenido
check() {
  if [ "$2" = "$3" ]; then pass "$1"; else fail "$1 (esperado: $2, obtenido: $3)"; fi
}

json() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

status() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

echo "Imagen: $IMAGE"
echo "Levantando la aplicación y PostgreSQL (puede tardar un minuto)..."
if ! compose up -d --wait --wait-timeout 180; then
  FAILURES=$((FAILURES + 1))
  echo "La aplicación no llegó a estar sana."
  exit 1
fi

echo "Comprobaciones:"
check "readiness responde 200" 200 "$(status "$BASE/actuator/health/readiness")"
check "liveness responde 200" 200 "$(status "$BASE/actuator/health/liveness")"
check "sin token, la administración responde 401" 401 "$(status "$BASE/api/v1/admin/users")"
check "Swagger UI desactivado en producción" 404 "$(status "$BASE/swagger-ui.html")"
check "OpenAPI desactivado en producción" 404 "$(status "$BASE/v3/api-docs")"

JWKS_KID="$(curl -s "$BASE/.well-known/jwks.json" | json "d['keys'][0]['kid']")"
check "JWKS publica la clave con su kid" "$JWT_KEY_ID" "$JWKS_KID"
JWKS_PRIVATE="$(curl -s "$BASE/.well-known/jwks.json" | json "'d' in d['keys'][0]")"
check "JWKS no incluye material privado" False "$JWKS_PRIVATE"

LOGIN_HEADERS="$WORK/login.headers"
# X-Forwarded-For simula un proxy inverso: la petición llega desde la red privada de Docker, que se
# considera un proxy de confianza, así que la IP registrada debe ser la del cliente original.
CLIENT_IP="203.0.113.7"
LOGIN_BODY="$(curl -s -D "$LOGIN_HEADERS" -X POST "$BASE/api/v1/auth/login" \
  -H 'Content-Type: application/json' -H 'X-Client-Id: auth-admin-web' \
  -H "X-Forwarded-For: $CLIENT_IP" \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}")"
TOKEN="$(echo "$LOGIN_BODY" | json "d['accessToken']" 2>/dev/null || true)"
if [ -n "$TOKEN" ]; then pass "el administrador inicial puede iniciar sesión"; else fail "login del administrador inicial"; fi

COOKIE_LINE="$(grep -i '^set-cookie: refresh_token=' "$LOGIN_HEADERS" || true)"
for attribute in HttpOnly Secure "SameSite=Strict" "Path=/api/v1/auth"; do
  if echo "$COOKIE_LINE" | grep -qi "$attribute"; then
    pass "la cookie del refresh token incluye $attribute"
  else
    fail "la cookie del refresh token incluye $attribute"
  fi
done

if [ -n "$TOKEN" ]; then
  CLAIMS="$(echo "$TOKEN" | cut -d. -f2 | python3 -c "
import sys, base64, json
s = sys.stdin.read().strip()
print(json.dumps(json.loads(base64.urlsafe_b64decode(s + '=' * (-len(s) % 4)))))")"
  check "el token lleva el issuer configurado" "$JWT_ISSUER" "$(echo "$CLAIMS" | json "d['iss']")"
  # `aud` puede ser un texto o una lista (el estándar admite ambas).
  check "el token es para auth-admin" auth-admin \
    "$(echo "$CLAIMS" | json "d['aud'] if isinstance(d['aud'], str) else d['aud'][0]")"
  check "con el token, la administración responde 200" 200 \
    "$(status -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/admin/users")"
  check "se registra la IP del cliente indicada por el proxy" "$CLIENT_IP" \
    "$(curl -s -H "Authorization: Bearer $TOKEN" "$BASE/api/v1/users/me/sessions" | json "d[0]['ipAddress']")"
fi

check "la aplicación no se ejecuta como root" 10001 "$(compose exec -T app id -u | tr -d '\r')"
check "el sistema de ficheros es de solo lectura" "Read-only file system" \
  "$(compose exec -T app sh -c 'touch /app/x 2>&1' | tr -d '\r' | sed 's/.*: //')"

echo
if [ "$FAILURES" -eq 0 ]; then
  echo "Prueba de humo superada."
else
  echo "Prueba de humo FALLIDA: $FAILURES comprobación(es) con error."
  exit 1
fi
