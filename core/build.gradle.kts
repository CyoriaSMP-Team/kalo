plugins {
    id("paper-conventions")
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

repositories {
    // PlaceholderAPI publishes to its own repository, not Maven Central.
    maven("https://repo.extendedclip.com/releases")
}

dependencies {
    implementation(project(":api"))
    compileOnly("com.github.bindglam:ConfigLib:1.0.0")
    compileOnly("org.incendo:cloud-paper:2.0.0")
    // Soft dependency: the hook is only touched when the plugin is installed.
    compileOnly("me.clip:placeholderapi:2.12.3")
}

paperPluginYaml {
    name = rootProject.name
    version = rootProject.version.toString()
    main = "$group.KaloPluginImpl"
    loader = "$group.KaloPluginLoader"
    apiVersion = property("paper_plugin_api_version").toString()
    author = "Kalo"
    foliaSupported = true
    // PlaceholderAPI is not declared here on purpose: the hook registers on
    // ServerLoadEvent, by which point every plugin is loaded, so load order does not
    // matter and servers without it need no special handling.
    dependencies {
    }
}
