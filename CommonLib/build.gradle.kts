// Mark this as a library module (not a plugin)
// This prevents the cloudstream gradle plugin from being applied
ext.set("isLibraryModule", true)

// No version or cloudstream {} block needed - this is a library, not a plugin

dependencies {
    // AndroidX dependencies needed for shared utilities
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.google.android.material:material:1.13.0")
}
