version = 2

cloudstream {
    description = "7mmTV - JAV Streaming"
    authors = listOf("rjbiermann")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=7mmtv.sx&sz=%size%"
    language = "en"
}

dependencies {
    implementation(project(":CommonLib"))
    implementation("com.google.android.material:material:1.13.0")
}
