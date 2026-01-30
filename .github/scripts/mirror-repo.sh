#!/usr/bin/env bash
#
# mirror-repo.sh — Mirror a GitHub repository to Codeberg
#
# Uses git credential helper to avoid embedding tokens in URLs (which
# would leak in CI logs).
#
# Usage:
#   mirror-repo.sh <github-repo-url> <codeberg-repo-url>
#
# Environment:
#   GITHUB_TOKEN     Token for cloning from GitHub
#   CODEBERG_TOKEN   Token for pushing to Codeberg
#   GH_USERNAME      Git username for both remotes
#
# Exit codes:
#   0  Success
#   1  Clone or push failure
#

set -euo pipefail

readonly GITHUB_URL="${1:?Usage: $0 <github-repo-url> <codeberg-repo-url>}"
readonly CODEBERG_URL="${2:?Usage: $0 <github-repo-url> <codeberg-repo-url>}"
readonly GH_USERNAME="${GH_USERNAME:?GH_USERNAME must be set}"

error() { printf '::error::%s\n' "$*" >&2; }
log()   { printf '%s\n' "$*"; }

store_credential() {
  local url="$1" username="$2" password="$3"
  {
    echo "url=$url"
    echo "username=$username"
    echo "password=$password"
    echo ""
  } | git credential approve
}

main() {
  git config --global credential.helper store

  store_credential "$GITHUB_URL" "$GH_USERNAME" "${GITHUB_TOKEN:?GITHUB_TOKEN must be set}"
  store_credential "$CODEBERG_URL" "$GH_USERNAME" "${CODEBERG_TOKEN:?CODEBERG_TOKEN must be set}"

  if ! git clone --bare "$GITHUB_URL"; then
    error "Failed to clone source repository"
    exit 1
  fi

  # Extract directory name from URL (e.g., "probable-octo-chainsaw.git")
  local repo_dir
  repo_dir=$(basename "$GITHUB_URL")
  cd "$repo_dir"

  git remote add mirror "$CODEBERG_URL"

  if ! git push mirror --all --force; then
    error "Failed to push branches to mirror"
    exit 1
  fi

  if ! git push mirror --tags --force; then
    error "Failed to push tags to mirror"
    exit 1
  fi

  log "Successfully mirrored to Codeberg"
}

main
