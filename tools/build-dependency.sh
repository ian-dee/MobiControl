#!/usr/bin/env bash
set -euo pipefail
app_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dependency_revision="$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("revision")' "$app_root/dependencies/pumpx2.json")"
dependency_url="$(ruby -rjson -e 'puts JSON.parse(File.read(ARGV[0])).fetch("repository")' "$app_root/dependencies/pumpx2.json")"
dependency_root="$app_root/.dependencies/pumpX2"
if [[ ! -d "$dependency_root/.git" ]]; then
  git clone --no-checkout --no-tags "${PUMPX2_SOURCE:-$dependency_url}" "$dependency_root"
elif [[ -n "$(git -C "$dependency_root" status --porcelain)" ]]; then
  echo 'Dependency checkout is modified. Preserve your edits; use a fresh app checkout.' >&2
  exit 1
fi
if ! git -C "$dependency_root" cat-file -e "$dependency_revision^{commit}"; then
  git -C "$dependency_root" fetch --no-tags origin "$dependency_revision"
fi
git -C "$dependency_root" checkout --detach "$dependency_revision"
test "$(git -C "$dependency_root" rev-parse HEAD)" = "$dependency_revision"
cd "$dependency_root"
# This task publishes only into this app checkout. No GitHub Packages or release upload.
./gradlew --no-daemon --no-build-cache --console=plain "-Dmaven.repo.local=$app_root/.dependencies/maven" \
  :androidLib:publishToMavenLocal :messages:publishToMavenLocal :shared:publishToMavenLocal
