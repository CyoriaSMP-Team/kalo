plugins {
    id("paper-conventions")
}

dependencies {
    compileOnly(project(":api"))
    // compileOnly does not reach the test classpath, and the descriptor test loads the
    // bridge classes to check their JVM signatures.
    testImplementation(project(":api"))
}

tasks.jar {
    // The plugin name inside plugin.yml must stay "Nexo" because consumers hardcode it.
    // The file name does not, so operators can see what they installed.
    archiveBaseName.set("Kalo-Compat-Nexo")
}
