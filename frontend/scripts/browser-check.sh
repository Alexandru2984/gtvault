#!/usr/bin/env bash
set -euo pipefail
umask 077

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)
cd "$repo_root"
test -f target/gt-shop-3.0.0.jar || { printf 'Rulează mvn verify înainte de verificarea în browser.\n' >&2; exit 1; }
preview_dir=$(mktemp -d /tmp/rewardhub-browser.XXXXXX)
preview_container=
preview_backend_pid=

cleanup() {
    if test -f "$preview_dir/nginx.pid"; then
        nginx -p "$preview_dir" -c "$preview_dir/nginx.conf" -s quit 2>/dev/null || true
    fi
    if test -n "$preview_backend_pid"; then
        kill "$preview_backend_pid" 2>/dev/null || true
        wait "$preview_backend_pid" 2>/dev/null || true
    fi
    if test -n "$preview_container"; then docker rm -f "$preview_container" >/dev/null 2>&1 || true; fi
    mkdir -p test-results/browser
    cp "$preview_dir/"*.log test-results/browser/ 2>/dev/null || true
    rm -rf -- "$preview_dir"
}
trap cleanup EXIT

free_port() {
    python3 -c 'import socket; sock = socket.socket(); sock.bind(("127.0.0.1", 0)); print(sock.getsockname()[1])'
}

preview_container=$(docker run -d --rm --label project=rewardhub-browser-check \
    -e POSTGRES_PASSWORD=isolated-browser-check -p 127.0.0.1::5432 postgres:18.6-alpine)
for _ in {1..60}; do
    # Serverul temporar de initdb accepta numai socketuri Unix. TCP indica instanta finala.
    if docker exec "$preview_container" pg_isready -h 127.0.0.1 -U postgres >/dev/null 2>&1; then break; fi
    sleep 1
done
docker exec "$preview_container" psql -U postgres -v ON_ERROR_STOP=1 \
    -c "CREATE ROLE rewardhub_test LOGIN PASSWORD 'isolated-browser-app'" \
    -c 'CREATE DATABASE rewardhub_test OWNER rewardhub_test' >/dev/null
preview_database=$(docker port "$preview_container" 5432/tcp)
preview_api_port=$(free_port)
preview_site_port=$(free_port)
cp target/gt-shop-3.0.0.jar "$preview_dir/backend.jar"

SERVER_PORT="$preview_api_port" \
DATABASE_URL="jdbc:postgresql://$preview_database/rewardhub_test" \
DATABASE_USER=rewardhub_test DATABASE_PASSWORD=isolated-browser-app \
JWT_SECRET="$(openssl rand -hex 32)" \
java -jar "$preview_dir/backend.jar" > "$preview_dir/backend.log" 2>&1 &
preview_backend_pid=$!
preview_healthy=false
for _ in {1..90}; do
    if curl -fsS "http://127.0.0.1:$preview_api_port/RewardHub/actuator/health" >/dev/null 2>&1; then preview_healthy=true; break; fi
    if ! kill -0 "$preview_backend_pid" 2>/dev/null; then break; fi
    sleep 1
done
if test "$preview_healthy" != true; then tail -40 "$preview_dir/backend.log"; exit 1; fi

REWARDHUB_CATALOG_URL="http://127.0.0.1:$preview_api_port/RewardHub/api/rewards" npm run build:frontend
node frontend/scripts/check-build.mjs

sed "s/127.0.0.1:8087/127.0.0.1:$preview_api_port/" deploy/nginx/rewardhub-http.conf > "$preview_dir/http.conf"
cat > "$preview_dir/nginx.conf" <<EOF
pid $preview_dir/nginx.pid;
error_log $preview_dir/nginx.log;
events {}
http {
    include /etc/nginx/mime.types;
    include $preview_dir/http.conf;
    geo \$remote_addr \$from_cloudflare_origin { default 0; 127.0.0.1/32 1; }
    access_log off;
    client_body_temp_path $preview_dir/body;
    proxy_temp_path $preview_dir/proxy;
    fastcgi_temp_path $preview_dir/fastcgi;
    uwsgi_temp_path $preview_dir/uwsgi;
    scgi_temp_path $preview_dir/scgi;
    server {
        listen 127.0.0.1:$preview_site_port;
        root $repo_root/target/frontend;
        include $repo_root/deploy/nginx/rewardhub-site.conf;
    }
}
EOF
nginx -t -p "$preview_dir" -c "$preview_dir/nginx.conf"
nginx -p "$preview_dir" -c "$preview_dir/nginx.conf"
preview_url="http://127.0.0.1:$preview_site_port"

curl -fsS "$preview_url/RewardHub/api/auth/register" -H 'Content-Type: application/json' \
    --data '{"email":"admin@rewardhub.test","name":"Admin Test","password":"Parola-Release-123"}' >/dev/null
docker exec "$preview_container" psql -U postgres -d rewardhub_test -v ON_ERROR_STOP=1 \
    -c "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@rewardhub.test'" >/dev/null

REWARDHUB_TEST_URL="$preview_url" npm run test:browser
