#!/usr/bin/env bash
#
# push-builds.sh — Commit and force-push build artifacts to the builds branch
#
# Amends the single commit on the builds branch (or creates an initial commit)
# and force-pushes. This keeps the builds branch as a single-commit branch
# containing only the latest artifacts.
#
# Usage:
#   push-builds.sh <builds-dir> <commit-sha>
#
# Exit codes:
#   0  Success (or no changes to push)
#   1  Git failure
#

set -euo pipefail

readonly BUILDS_DIR="${1:?Usage: $0 <builds-dir> <commit-sha>}"
readonly COMMIT_SHA="${2:?Usage: $0 <builds-dir> <commit-sha>}"

log()    { printf '%s\n' "$*"; }
notice() { printf '::notice::%s\n' "$*"; }
error()  { printf '::error::%s\n' "$*" >&2; }

main() {
  if [[ ! -d "$BUILDS_DIR" ]]; then
    error "Builds directory does not exist: $BUILDS_DIR"
    exit 1
  fi

  cd "$BUILDS_DIR"

  git config user.name "github-actions[bot]"
  git config user.email "github-actions[bot]@users.noreply.github.com"
  git add .

  if git diff --cached --quiet; then
    notice "No changes to commit to builds branch"
    exit 0
  fi

  if git rev-parse HEAD >/dev/null 2>&1; then
    if ! git commit --amend -m "Build $COMMIT_SHA"; then
      error "Failed to commit build artifacts"
      exit 1
    fi
  else
    notice "Builds branch is new, creating initial commit"
    if ! git commit -m "Build $COMMIT_SHA"; then
      error "Failed to commit build artifacts"
      exit 1
    fi
  fi

  if ! git push --force; then
    error "Failed to push builds branch"
    exit 1
  fi

  log "Successfully pushed builds"
}

main
