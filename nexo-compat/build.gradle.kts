plugins {
    id("paper-conventions")
    kotlin("jvm") version "2.4.21"
}

// Embedded runtime component. Its classes and Kotlin runtime are shaded into Kalo's single jar.
dependencies {
    compileOnly(project(":api"))
    implementation(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("stdlib"))
    testImplementation(project(":api"))
}

kotlin {
    jvmToolchain(21)
}
