version = 12

cloudstream {
    description = "PornXP"
    authors = listOf("rjbiermann")
    status = 1
    tvTypes = listOf("NSFW")
    iconUrl = "https://www.google.com/s2/favicons?domain=pornxp.ph&sz=%size%"
    language = "en"
}

dependencies {
    implementation(project(":CommonLib"))
    implementation("com.google.android.material:material:1.13.0")

    // Test dependencies for architecture components
    testImplementation(project(":CommonLib"))
}
