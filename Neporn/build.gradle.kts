version = 7

cloudstream {
    description = "Neporn"
    authors = listOf("rjbiermann")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=neporn.com&sz=%size%"
    language = "en"
}

dependencies {
    implementation(project(":CommonLib"))
    implementation("com.google.android.material:material:1.13.0")
}
