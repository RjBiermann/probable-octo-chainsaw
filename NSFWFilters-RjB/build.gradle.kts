dependencies {
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
// use an integer for version numbers
version = 3


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "NSFWFilters-RjB"
    authors = listOf("RjB (Coxju)")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1

    tvTypes = listOf("NSFW")

    requiresResources = true
    language = "en"

    // random cc logo i found
    iconUrl = "https://static-00.iconduck.com/assets.00/nsfw-icon-78x96-5m773s6x.png"
}

android {
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}