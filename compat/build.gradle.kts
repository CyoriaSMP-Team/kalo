plugins {
    id("paper-conventions")
    kotlin("jvm") version "2.4.21"
}

// Compatibility layer, embedded in the Kalo jar: where a plugin is a stand-in for Kalo (it
// defines the items/blocks/models other plugins consume), Kalo answers its API. Consumers of
// Kalo content are not impersonated. See docs/COMPAT.md.
dependencies {
    compileOnly(project(":api"))
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("stdlib"))
    testImplementation(project(":api"))
}

kotlin {
    jvmToolchain(21)
}
