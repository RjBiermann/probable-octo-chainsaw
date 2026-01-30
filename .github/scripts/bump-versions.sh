#!/usr/bin/env bash
#
# bump-versions.sh — Intelligent version bumping for Cloudstream plugins
#
# Detects which plugins have source-code changes and increments their
# version numbers in build.gradle.kts. Commits and pushes the result.
#
# Usage:
#   bump-versions.sh --before <sha> --after <sha> [--dry-run]
#   bump-versions.sh --bump-all [--dry-run]
#
# Options:
#   --before <sha>   Git SHA before the push event
#   --after <sha>    Git SHA after the push event
#   --bump-all       Bump every plugin regardless of changes
#   --dry-run        Print what would happen without modifying anything
#
# Exit codes:
#   0  Success (versions bumped, or nothing to bump)
#   1  Error (parse failure, git failure, etc.)
#
# Environment:
#   GITHUB_OUTPUT    If set, writes outputs for GitHub Actions consumption
#

set -euo pipefail

# --- Configuration -----------------------------------------------------------

# Only changes matching these patterns inside a plugin directory trigger a bump.
# Changes outside these paths (tests, docs, CI config) do not bump versions.
SOURCE_PATTERNS=(
  "src/main/"
  "build.gradle.kts"
)

readonly BOT_NAME="github-actions[bot]"
readonly BOT_EMAIL="github-actions[bot]@users.noreply.github.com"
readonly SKIP_TAG="[skip-bump]"

# --- Globals -----------------------------------------------------------------

DRY_RUN=false
BUMP_ALL=false
BEFORE_SHA=""
AFTER_SHA=""
CHANGED_FILES=""

# --- Helpers -----------------------------------------------------------------

log()   { printf '%s\n' "$*"; }
warn()  { printf '::warning::%s\n' "$*" >&2; }
error() { printf '::error::%s\n' "$*" >&2; }
notice(){ printf '::notice::%s\n' "$*"; }

# Write a key=value pair to GITHUB_OUTPUT if available, otherwise log it.
gh_output() {
  local key="$1" value="$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    printf '%s=%s\n' "$key" "$value" >> "$GITHUB_OUTPUT"
  else
    log "[output] $key=$value"
  fi
}

# --- Functions ---------------------------------------------------------------

parse_args() {
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --before)   BEFORE_SHA="$2"; shift 2 ;;
      --after)    AFTER_SHA="$2";  shift 2 ;;
      --bump-all) BUMP_ALL=true;   shift ;;
      --dry-run)  DRY_RUN=true;    shift ;;
      -h|--help)  usage; exit 0 ;;
      *) error "Unknown option: $1"; usage; exit 1 ;;
    esac
  done

  if [[ "$BUMP_ALL" != "true" && ( -z "$BEFORE_SHA" || -z "$AFTER_SHA" ) ]]; then
    error "Must provide --before and --after SHAs, or use --bump-all"
    usage
    exit 1
  fi
}

usage() {
  log "Usage: $0 --before <sha> --after <sha> [--dry-run]"
  log "       $0 --bump-all [--dry-run]"
}

# Populates CHANGED_FILES with changed file paths (one per line).
# Sets BUMP_ALL=true if diff fails (e.g., force-push) or CommonLib changed.
detect_changed_files() {
  if [[ "$BEFORE_SHA" == "0000000000000000000000000000000000000000" ]]; then
    CHANGED_FILES=$(git diff --name-only HEAD~1 HEAD 2>/dev/null) || {
      warn "Cannot diff initial commit, bumping all plugins"
      BUMP_ALL=true
      return
    }
  else
    CHANGED_FILES=$(git diff --name-only "$BEFORE_SHA" "$AFTER_SHA" 2>/dev/null) || {
      warn "Cannot diff ${BEFORE_SHA}..${AFTER_SHA} (force-push?), bumping all plugins"
      BUMP_ALL=true
      return
    }
  fi

  if printf '%s\n' "$CHANGED_FILES" | grep -q "^CommonLib/"; then
    notice "CommonLib changed — bumping all plugins"
    BUMP_ALL=true
  fi
}

# Prints plugin directory names, one per line.
discover_plugins() {
  local count=0
  for dir in */; do
    local name="${dir%/}"
    if [[ -f "$name/build.gradle.kts" && "$name" != "CommonLib" ]]; then
      printf '%s\n' "$name"
      ((count++))
    fi
  done

  if [[ $count -eq 0 ]]; then
    error "No plugins found"
    exit 1
  fi
  log "Found $count plugins" >&2
}

# Given a list of changed files and a plugin name, returns 0 if the plugin
# has source-code changes that warrant a version bump.
plugin_has_source_changes() {
  local plugin="$1"
  local changed_files="$2"

  for pattern in "${SOURCE_PATTERNS[@]}"; do
    if printf '%s\n' "$changed_files" | grep -q "^${plugin}/${pattern}"; then
      return 0
    fi
  done
  return 1
}

# Selects which plugins need bumping. Prints names one per line.
select_plugins_to_bump() {
  local changed_files="$1"
  local plugins
  plugins=$(discover_plugins)

  if [[ "$BUMP_ALL" == "true" ]]; then
    printf '%s\n' "$plugins"
    return
  fi

  while IFS= read -r plugin; do
    if plugin_has_source_changes "$plugin" "$changed_files"; then
      printf '%s\n' "$plugin"
    fi
  done <<< "$plugins"
}

# Reads the current integer version from a plugin's build.gradle.kts.
read_version() {
  local gradle_file="$1"
  local version
  version=$(grep -E "^version[[:space:]]*=" "$gradle_file" \
    | sed -E 's/version[[:space:]]*=[[:space:]]*//' \
    | tr -d ' "'"'" \
    | head -1)

  if ! [[ "$version" =~ ^[0-9]+$ ]]; then
    error "Cannot parse version in $gradle_file (got '$version')"
    exit 1
  fi
  printf '%s' "$version"
}

# Increments the version in a plugin's build.gradle.kts.
# Prints "plugin: old -> new" to stdout.
bump_plugin() {
  local plugin="$1"
  local gradle_file="$plugin/build.gradle.kts"
  local current new

  current=$(read_version "$gradle_file")
  new=$((current + 1))

  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] $plugin: $current -> $new"
    return
  fi

  sed -i -E "s/^version[[:space:]]*=.*/version = $new/" "$gradle_file"

  # Verify the write succeeded
  local actual
  actual=$(read_version "$gradle_file")
  if [[ "$actual" != "$new" ]]; then
    error "Version update failed for $plugin (expected $new, got '$actual')"
    exit 1
  fi

  log "  $plugin: $current -> $new"
}

# Stages, commits, and pushes version bump changes.
commit_and_push() {
  local -a plugins_arr=("$@")

  git config user.name "$BOT_NAME"
  git config user.email "$BOT_EMAIL"

  for plugin in "${plugins_arr[@]}"; do
    local file="$plugin/build.gradle.kts"
    if [[ ! -f "$file" ]]; then
      error "Expected file not found: $file"
      exit 1
    fi
    git add "$file"
  done

  if git diff --cached --quiet; then
    error "No version changes were staged — this should not happen"
    exit 1
  fi

  local plugin_list
  plugin_list=$(printf ' %s' "${plugins_arr[@]}")

  if ! git commit -m "chore: bump plugin versions for:${plugin_list} ${SKIP_TAG}"; then
    error "Failed to commit version bump"
    exit 1
  fi

  if ! git push; then
    error "Failed to push version bump. Check branch protection settings."
    error "Add '$BOT_NAME' to bypass list if branch is protected."
    exit 1
  fi

  notice "Committed and pushed version bump for:${plugin_list}"
}

# --- Main --------------------------------------------------------------------

main() {
  parse_args "$@"

  if [[ "$BUMP_ALL" != "true" ]]; then
    detect_changed_files
  fi

  local plugins_to_bump
  plugins_to_bump=$(select_plugins_to_bump "$CHANGED_FILES")

  if [[ -z "$plugins_to_bump" ]]; then
    log "No plugins to bump"
    gh_output "bumped" "false"
    exit 0
  fi

  log "Plugins to bump:"
  printf '%s\n' "$plugins_to_bump" | sed 's/^/  /'

  # Bump each plugin
  local -a bumped_arr=()
  while IFS= read -r plugin; do
    bump_plugin "$plugin"
    bumped_arr+=("$plugin")
  done <<< "$plugins_to_bump"

  # Commit and push (unless dry-run)
  if [[ "$DRY_RUN" == "true" ]]; then
    log "[dry-run] Would commit and push ${#bumped_arr[@]} version bumps"
  else
    commit_and_push "${bumped_arr[@]}"
  fi

  local bumped_list
  bumped_list=$(printf ' %s' "${bumped_arr[@]}")
  gh_output "bumped" "true"
  gh_output "bumped_plugins" "$bumped_list"
}

main "$@"
