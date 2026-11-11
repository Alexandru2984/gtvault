#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"
test -f target/gt-shop-3.0.0.jar
test -f target/frontend/sitemap.xml
release_id="rewardhub-$(date -u +%Y%m%dT%H%M%SZ)-$(git rev-parse --short HEAD)"
release_dir="target/$release_id"
mkdir "$release_dir"
cp target/gt-shop-3.0.0.jar "$release_dir/backend.jar"
cp -a target/frontend "$release_dir/frontend"
mkdir "$release_dir/nginx" "$release_dir/db"
cp deploy/nginx/rewardhub-http.conf deploy/nginx/rewardhub-site.conf "$release_dir/nginx/"
cp deploy/db/00-transfer-ownership.sql "$release_dir/db/"
cp deploy/README.md "$release_dir/OPERATIONS.md"
git rev-parse HEAD > "$release_dir/REVISION"
(
    cd "$release_dir"
    mapfile -d '' -t release_files < <(find . -type f -print0 | sort -z)
    sha256sum "${release_files[@]}" > SHA256SUMS
    sha256sum -c SHA256SUMS > /dev/null
)
tar -czf "target/$release_id.tar.gz" -C target "$release_id"
printf '%s\n' "target/$release_id.tar.gz"
