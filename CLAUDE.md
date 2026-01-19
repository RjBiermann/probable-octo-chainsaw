# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cloudstream3 plugin repository containing NSFW video streaming plugins. Each top-level directory with a `build.gradle.kts` is a separate Android library plugin that compiles to a `.cs3` file.

Current plugins: Fullporner, HQPorner, MissAV, Neporn, Perverzija, PornHits, PornTrex, PornXp

## Build Commands

```bash
./gradlew make                  # Build all plugins (outputs .cs3 files)
./gradlew makePluginsJson       # Generate plugins.json manifest
./gradlew test                  # Run all tests
./gradlew :PluginName:test      # Run single plugin's tests (e.g., :Neporn:test)
./gradlew clean                 # Clean build directory
```

## Architecture

### Plugin Structure

```
PluginName/
├── build.gradle.kts          # Metadata: version, description, authors, tvTypes, iconUrl
└── src/
    ├── main/kotlin/com/lagradost/
    │   ├── *Plugin.kt        # Entry point (@CloudstreamPlugin) - loads settings, registers MainAPI
    │   ├── *.kt              # MainAPI subclass - implements search, load, loadLinks
    │   ├── *SettingsFragment.kt  # Programmatic Material UI (no XML layouts)
    │   ├── *UrlValidator.kt  # Object singleton for URL parsing/validation
    │   ├── *Extractor.kt     # (Optional) ExtractorApi for external video hosts
    │   ├── ValidationResult.kt   # Sealed class: Valid(path, label), InvalidDomain, InvalidPath
    │   ├── CustomPage.kt     # User-defined homepage sections (JSON-serializable)
    │   ├── CustomPagesAdapter.kt # RecyclerView adapter for settings list
    │   └── TvFocusUtils.kt   # D-pad navigation helpers for Android TV
    └── test/kotlin/
        └── *UrlValidatorTest.kt  # Unit tests for URL validation
```

### MainAPI Implementation

The MainAPI subclass is the core of each plugin with four required methods:

1. **`getMainPage(page, request)`** - Fetches homepage sections, handles pagination
2. **`search(query)`** - Searches the site, returns `List<SearchResponse>`
3. **`load(url)`** - Loads video details page, returns `LoadResponse` with metadata
4. **`loadLinks(data, ...)`** - Extracts actual video URLs via `callback(ExtractorLink)`

Video URLs are found via:
- JSON-LD schema (`script[type=application/ld+json]`)
- JavaScript variables (flashvars, player config)
- Regex extraction from page source

### Video Extractors

Some plugins use separate `ExtractorApi` classes for external video hosts:
- `HQWOExtractor` - hqwo.cc / bigcdn.cc
- `MyDaddyExtractor` - mydaddy.cc
- `FullpornerExtractor` - embed.fullporner.com
- `Playhydrax`, `Xtremestream` - Various embed players

Extractors register with `addExtractor()` in the Plugin class and implement `getUrl()` to parse embed pages.

### Settings UI Pattern

All settings UIs are built programmatically (no XML) using Material components:
- `BottomSheetDialog` for phones, `AlertDialog` for TV (better D-pad nav)
- `TextInputLayout` with validation feedback
- `RecyclerView` with `ItemTouchHelper` for drag-reorder (touch mode only)
- Theme colors resolved at runtime from Cloudstream attributes with Android fallbacks

### TV Mode Support

Each plugin detects TV mode via `TvFocusUtils.isTvMode()` and adapts:
- Explicit `focusable=true` on interactive elements
- Move up/down buttons instead of drag-and-drop
- Focus loops (last→first element wrapping)
- Wider dialogs for TV visibility

### Shared Code

These files are duplicated across plugins (no shared module):
- `ValidationResult.kt` - Sealed class for validation
- `CustomPage.kt` - Data class with JSON serialization
- `CustomPagesAdapter.kt` - RecyclerView adapter
- `CustomPageItemTouchHelper.kt` - Drag-and-drop support
- `TvFocusUtils.kt` - Android TV helpers

## Adding a New Plugin

1. Create directory at project root (e.g., `NewSite/`)
2. Add `build.gradle.kts` with cloudstream metadata block (copy from existing plugin)
3. Add Material dependency if using settings UI: `implementation("com.google.android.material:material:1.13.0")`
4. Create source files in `com.lagradost` package
5. Plugin is auto-discovered by `settings.gradle.kts`

## Dependencies

Provided by root build.gradle.kts:
- `cloudstream3:pre-release` - Core API stubs
- `NiceHttp:0.4.13` - HTTP client (`app.get()`, `app.post()`)
- `jsoup:1.18.3` - HTML parsing via CSS selectors
- `kotlinx-coroutines-core:1.10.1` - Async operations
- `fuzzywuzzy:1.4.0` - String matching

Test dependencies: JUnit 4, kotlin-test, mockk

## Testing

Tests focus on URL validation (pure Kotlin, no Android deps). `unitTests.isReturnDefaultValues = true` in build config allows mocking Android's `Log` class.

## Troubleshooting

### Kotlin Version Mismatch

If CI fails with errors like:
```
Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is X.X.X, expected version is Y.Y.Y
```

The `cloudstream3:pre-release` dependency was compiled with a newer Kotlin version. Fix by updating in root `build.gradle.kts`:
- `kotlin-gradle-plugin` version in buildscript dependencies
- `kotlin-test` version in test dependencies

### Verify Builds Locally Before Pushing

Always run the full build locally to catch CI issues early:
```bash
./gradlew clean make    # Full build of all plugins
./gradlew test          # Run all tests
```

To test a single plugin quickly:
```bash
./gradlew :PluginName:compileDebugKotlin  # Just compile (fastest check)
./gradlew :PluginName:make                # Full plugin build
```

## Maintaining This File

Update CLAUDE.md when:
- Adding/removing plugins (update the plugin list in Project Overview)
- Adding new shared patterns or utility files
- Changing build commands or dependencies
- Introducing new architectural patterns (e.g., new extractor types)
