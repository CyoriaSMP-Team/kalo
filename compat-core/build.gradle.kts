plugins {
    id("paper-conventions")
}

// Shared by every compatibility shim. Holds the two things they must agree on: how a plugin's
// flat item id maps to a namespaced Kalo key, and how a Kalo item is found and recognised.
dependencies {
    compileOnly(project(":api"))
    testImplementation(project(":api"))
}
