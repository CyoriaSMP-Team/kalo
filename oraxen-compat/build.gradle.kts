plugins {
    id("paper-conventions")
}

// Kalo answering the Oraxen item API (io.th0rgal.oraxen.*). Embedded in the Kalo jar; a stand-in
// plugin for Kalo, so Kalo impersonates it. Plain Java because Oraxen itself is plain Java:
// its API is static methods, with no Kotlin `object`/INSTANCE to mirror.
dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":compat-core"))
    testImplementation(project(":api"))
    testImplementation(project(":compat-core"))
}
