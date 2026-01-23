# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Cloudstream3 plugin repository containing NSFW video streaming plugins. Each top-level directory with a `build.gradle.kts` is a separate Android library plugin that compiles to a `.cs3` file.

Current plugins: Fullporner, HQPorner, MissAV, Neporn, NsfwUltima, Perverzija, PornHits, PornTrex, PornXp

## AI Policy

When using AI assistance for contributions to this repository:

1. **Disclose AI usage** - Always state any AI usage in pull requests and issues
2. **Test before submitting** - Always test code before making a pull request. We do not want to test your AI generated code
3. **Humans over AI** - Listen to humans over computers. Contributors to CloudStream know this codebase better than an AI
4. **Understand your code** - You should be able to explain and fix any code you submit. We do in-depth reviews and will reject low effort contributions

## Build Commands

```bash
./gradlew make                  # Build all plugins (outputs .cs3 files)
./gradlew makePluginsJson       # Generate plugins.json manifest
./gradlew test                  # Run all tests
./gradlew :PluginName:test      # Run single plugin's tests (e.g., :Neporn:test)
./gradlew clean                 # Clean build directory
./gradlew build --warning-mode all  # Show deprecation warnings
```

CI uses `--parallel --build-cache --no-daemon` flags for optimal performance.

## Architecture

### Plugin Structure

```
PluginName/
├── build.gradle.kts          # Metadata: version, description, authors, tvTypes, iconUrl
└── src/
    ├── main/res/drawable/    # Plugin-bundled drawables (icons, vectors)
    ├── main/kotlin/com/lagradost/
    │   ├── *Plugin.kt        # Entry point (@CloudstreamPlugin) - loads settings, registers MainAPI
    │   ├── *.kt              # MainAPI subclass - implements search, load, loadLinks
    │   ├── *SettingsFragment.kt  # Programmatic Material UI (no XML layouts)
    │   ├── *UrlValidator.kt  # Object singleton for URL parsing/validation
    │   └── *Extractor.kt     # (Optional) ExtractorApi for external video hosts
    └── test/kotlin/
        └── *UrlValidatorTest.kt  # Unit tests for URL validation
```

Shared utilities (ValidationResult, CustomPage, CustomPagesAdapter, TvFocusUtils) are imported from CommonLib—see [CommonLib Shared Module](#commonlib-shared-module).

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
- `FullpornerExtractor` - xiaoshenke.net (video player used by fullporner.com)
- `Playhydrax`, `Xtremestream` - Various embed players

Extractors register with `addExtractor()` in the Plugin class and implement `getUrl()` to parse embed pages.

### Settings UI Pattern

All settings UIs are built programmatically (no XML) using Material components:
- `BottomSheetDialog` for phones, `AlertDialog` for TV (better D-pad nav)
- `TextInputLayout` with validation feedback
- `RecyclerView` with `ItemTouchHelper` for drag-reorder (touch mode only)
- Theme colors resolved at runtime from Cloudstream attributes with Android fallbacks

**Storage error handling:** Storage save methods should return `Boolean` success/failure. Callers must check the return value and show user feedback (Toast) on failure. Never silently ignore save failures.

**Dialog callbacks:** When dialogs modify data and call parent callbacks (e.g., `onGroupsChanged`), the parent must call UI refresh methods (e.g., `refreshGroupedView()`) to update the display. Changes won't reflect automatically.

### TV Mode Support

Each plugin detects TV mode via `TvFocusUtils.isTvMode()` and adapts:
- Explicit `focusable=true` on interactive elements
- Move up/down buttons instead of drag-and-drop
- Focus loops (last→first element wrapping)
- Wider dialogs for TV visibility

### CommonLib Shared Module

Shared utilities live in `CommonLib/src/main/kotlin/com/lagradost/common/`:
- `ValidationResult.kt` - Sealed class: Valid(path, label), InvalidDomain, InvalidPath
- `CustomPage.kt` - Data class with JSON serialization (robust error handling)
- `CustomPagesAdapter.kt` - RecyclerView adapter for custom pages list
- `CustomPageItemTouchHelper.kt` - Drag-and-drop support for touch mode
- `TvFocusUtils.kt` - Android TV detection and D-pad navigation helpers

**Using CommonLib in a plugin:**
```kotlin
// In plugin's build.gradle.kts
dependencies {
    implementation(project(":CommonLib"))
}

// In Kotlin files
import com.lagradost.common.CustomPage
import com.lagradost.common.ValidationResult
```

**Important:** Plugin-specific PREFS_NAME constants and load/save helpers belong in the Plugin class, not CommonLib. CommonLib only provides the data structures and utilities.

**Build system:** The root `build.gradle.kts` has a centralized copy task that bundles CommonLib classes into each plugin before DEX compilation. This works around the CloudStream gradle plugin limitation (only DEX-compiles the current module's classes).

### Plugin Resources

Plugins can bundle their own drawable resources in `src/main/res/drawable/`. To load them at runtime:

```kotlin
// CORRECT - uses host package name (resources are merged at load time)
val resId = context.resources.getIdentifier("icon_name", "drawable", context.packageName)

// WRONG - hardcoded package name fails for plugin-bundled resources
val resId = context.resources.getIdentifier("icon_name", "drawable", "com.lagradost.cloudstream3")
```

When copying vector drawables from Cloudstream, replace `?attr/white` with `@android:color/white` for compatibility.

## Adding a New Plugin

1. Create directory at project root (e.g., `NewSite/`)
2. Add `build.gradle.kts` with cloudstream metadata block (copy from existing plugin)
3. Add CommonLib dependency: `implementation(project(":CommonLib"))`
4. Add Material dependency if using settings UI: `implementation("com.google.android.material:material:1.13.0")`
5. Create source files in `com.lagradost` package (import shared utilities from `com.lagradost.common`)
6. Plugin is auto-discovered by `settings.gradle.kts`

## Dependencies

Provided by root build.gradle.kts:
- `cloudstream3:pre-release` - Core API stubs
- `NiceHttp:0.4.13` - HTTP client (`app.get()`, `app.post()`)
- `jsoup:1.18.3` - HTML parsing via CSS selectors
- `kotlinx-coroutines-core:1.10.1` - Async operations
- `fuzzywuzzy:1.4.0` - String matching

Test dependencies: JUnit 4, kotlin-test, mockk

## Version Compatibility

AGP, Kotlin, and Gradle versions must be compatible:
- Kotlin 2.3 requires AGP 8.13.2+ (includes R8 8.13.19 with Kotlin 2.3 metadata support)
- AGP 8.13.x requires Gradle 8.13+
- Check https://developer.android.com/build/kotlin-support for compatibility matrix

D8 warnings like "error parsing kotlin metadata" indicate AGP/Kotlin version mismatch.

## Testing

Tests focus on URL validation (pure Kotlin, no Android deps). `unitTests.isReturnDefaultValues = true` in build config allows mocking Android's `Log` class.

## Commit Workflow

**Before committing changes to any plugin, always increment its version number.**

The version is an integer at the top of each plugin's `build.gradle.kts`:
```kotlin
version = 3  // Increment this when making changes
```

### Pre-commit checklist:
1. **Bump version** - Increment `version = N` in `PluginName/build.gradle.kts`
2. **Build** - Run `./gradlew :PluginName:make` to verify compilation
3. **Test** - Run `./gradlew :PluginName:test` if tests exist
4. **Commit** - Include version bump in the same commit as the changes

### Why versioning matters:
- Cloudstream uses the version number to detect plugin updates
- Users won't receive updates if the version isn't incremented
- The `plugins.json` manifest includes version numbers for update checking

## Troubleshooting

### Kotlin Version Mismatch

If CI fails with errors like:
```
Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is X.X.X, expected version is Y.Y.Y
```

The `cloudstream3:pre-release` dependency was compiled with a newer Kotlin version. Fix by updating in root `build.gradle.kts`:
- `kotlin-gradle-plugin` version in buildscript dependencies
- `kotlin-test` version in test dependencies

### D8 Kotlin Metadata Warnings

If CI shows warnings like:
```
WARNING: D8: An error occurred when parsing kotlin metadata
```

The R8/D8 bundled with AGP doesn't support your Kotlin version. Fix by upgrading AGP to a version with compatible R8 (see Version Compatibility section above).

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

## Reference Sources

Local repositories for exploring Cloudstream APIs and plugin patterns. Paths are relative to the parent directory (`../`).

### Cloudstream App (Core APIs)

**Path:** `../cloudstream`
**GitHub:** https://github.com/recloudstream/cloudstream

The main Cloudstream app source. Useful for understanding:
- `MainAPI` base class and its methods
- `LoadResponse`, `SearchResponse`, `ExtractorLink` data structures
- Built-in extractors in `app/src/main/java/com/lagradost/cloudstream3/extractors/`
- Theme attributes for UI styling

## Maintaining This File

Update CLAUDE.md when:
- Adding/removing plugins (update the plugin list in Project Overview)
- Adding new shared patterns or utility files
- Changing build commands or dependencies
- Introducing new architectural patterns (e.g., new extractor types)
