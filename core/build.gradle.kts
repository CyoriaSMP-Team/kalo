plugins {
    id("paper-conventions")
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

dependencies {
    implementation(project(":api"))
    compileOnly("com.github.bindglam:ConfigLib:1.0.0")
    compileOnly("org.incendo:cloud-paper:2.0.0")
}

paperPluginYaml {
    name = rootProject.name
    version = rootProject.version.toString()
    main = "$group.KaloPluginImpl"
    loader = "$group.KaloPluginLoader"
    apiVersion = property("paper_plugin_api_version").toString()
    author = "Kalo"
    foliaSupported = true
    dependencies {
    }
}
