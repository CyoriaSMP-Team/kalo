plugins {
    id("standard-conventions")
}

repositories {
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    // Geyser only publishes its API as a snapshot; there is no release artifact.
    compileOnly("org.geysermc.geyser:api:2.11.1-SNAPSHOT")
    testImplementation("org.geysermc.geyser:api:2.11.1-SNAPSHOT")

    // Reads the mapping file Kalo's server plugin writes. Deliberately no dependency on
    // :api or :core — this runs inside Geyser, which is a different process from Paper,
    // so the two sides share a file format rather than classes.
    implementation("com.google.code.gson:gson:2.13.2")
}
