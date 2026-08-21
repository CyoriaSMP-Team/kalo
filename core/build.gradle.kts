import xyz.jpenilla.resourcefactory.paper.PaperPluginYaml
import org.gradle.api.tasks.testing.Test

plugins {
    id("paper-conventions")
    id("xyz.jpenilla.resource-factory-paper-convention") version "1.3.1"
}

repositories {
    // PlaceholderAPI publishes to its own repository, not Maven Central.
    maven("https://repo.extendedclip.com/releases")
    // Geyser publishes its API as a snapshot; its transitive dependencies
    // (base-api, cloudburst math) live in the releases repo alongside it.
    maven("https://repo.opencollab.dev/maven-releases")
    maven("https://repo.opencollab.dev/maven-snapshots")
}

dependencies {
    implementation(project(":api"))
    compileOnly("com.github.bindglam:ConfigLib:1.0.0")
    compileOnly("org.incendo:cloud-paper:2.0.0")
    // Soft dependency: the hook is only touched when the plugin is installed.
    compileOnly("me.clip:placeholderapi:2.12.3")
    // Soft dependency: touched only when Geyser shares this JVM.
    compileOnly("org.geysermc.geyser:api:2.11.1-SNAPSHOT")
}

tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

val performanceBenchmark by tasks.registering(Test::class) {
    group = "verification"
    description = "Runs Kalo's scale/performance macro benchmark"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("benchmark")
    }
    outputs.upToDateWhen { false }
    systemProperty(
        "kalo.benchmark.output",
        layout.buildDirectory.file("reports/benchmarks/kalo-performance.txt").get().asFile.absolutePath
    )
    testLogging.showStandardStreams = true
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
        // Paper plugins get isolated classloaders, unlike legacy Bukkit ones: without
        // joinClasspath a soft-depended plugin's classes are simply not visible, and the
        // hook fails with NoClassDefFoundError however carefully it is guarded.
        //
        // required = false so servers without Geyser are unaffected; Load.BEFORE so
        // Geyser is up before Kalo registers blocks with it.
        server("Geyser-Spigot", PaperPluginYaml.Load.BEFORE, false, true)

        // PlaceholderAPI needs no entry: the expansion is registered through its own API
        // on ServerLoadEvent, by which point every plugin is loaded.
    }
}
