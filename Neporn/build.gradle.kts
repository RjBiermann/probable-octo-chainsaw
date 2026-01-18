version = 3

cloudstream {
    description = "Neporn"
    authors = listOf("rjbiermann")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=neporn.com&sz=%size%"
    language = "en"
}

dependencies {
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
    implementation("com.google.android.material:material:1.13.0")
}
