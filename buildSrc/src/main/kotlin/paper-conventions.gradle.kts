plugins {
    id("standard-conventions")
}

val paperApiVersion = property("paper_api_version").toString()
dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    // compileOnly does not reach the test classpath, and tests need the same Adventure
    // and Gson the main sources compile against.
    testImplementation("io.papermc.paper:paper-api:$paperApiVersion")
}
