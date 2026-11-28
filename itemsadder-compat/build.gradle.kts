plugins {
    id("paper-conventions")
}

// Kalo answering the ItemsAdder item API (dev.lone.itemsadder.api). Embedded in the Kalo jar;
// ItemsAdder is a stand-in for Kalo (it defines the items other plugins consume), so Kalo
// impersonates it. Plain Java, like ItemsAdder's published API.
dependencies {
    compileOnly(project(":api"))
    compileOnly(project(":compat-core"))
    testImplementation(project(":api"))
    testImplementation(project(":compat-core"))
}
