plugins {
    // Provisions the Java 21 toolchain on machines that do not already have one.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Kalo"

include("api")
include("core")
