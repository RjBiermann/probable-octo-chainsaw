version = 1

cloudstream {
    description = "MissAV - JAV Streaming"
    authors = listOf("rjbiermann")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=missav.ws&sz=%size%"
    language = "en"
}

dependencies {
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
    implementation("com.google.android.material:material:1.13.0")
}
