version = 3

cloudstream {
    description = "Aggregates all NSFW plugins into one homepage with centralized feed management"
    authors = listOf("CloudstreamNSFW")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=github.com&sz=%size%"
    language = "en"
}

dependencies {
    implementation(project(":CommonLib"))
    implementation("com.google.android.material:material:1.13.0")
}
