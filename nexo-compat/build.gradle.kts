plugins {
    id("paper-conventions")
    kotlin("jvm") version "2.4.21"
}

// Optional add-on jar: Kalo answering as "Nexo" so plugins written against the Nexo API work
// with Kalo as the backend. It is deliberately NOT part of the main Kalo jar.
dependencies {
    compileOnly(project(":api"))
    // Not shaded: plugin.yml `libraries` makes the server download it at startup.
    compileOnly(kotlin("stdlib"))
    testImplementation(kotlin("test"))
    testImplementation(kotlin("stdlib"))
    testImplementation(project(":api"))
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    archiveBaseName.set("Kalo-NexoCompat")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
}

tasks.processResources {
    val pluginVersion = project.version.toString()
    inputs.property("pluginVersion", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}
