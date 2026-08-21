import xyz.jpenilla.runpaper.task.RunServer

plugins {
    id("standard-conventions")
    id("xyz.jpenilla.run-paper") version "3.1.0"
    id("com.gradleup.shadow") version "9.6.1"
}

dependencies {
    implementation(project(":core"))
}

val groupString = group.toString()
val mcVersionString = property("minecraft_version").toString()

// The jar is Java 21 bytecode so one build spans Paper 1.21.4 to 26.2 — but the *server*
// is a different question: Minecraft 26.1 and newer refuse to start on anything below
// Java 25. Inheriting the compile toolchain here made `runServer` fail outright the moment
// the toolchain moved down to 21. Compile low, run high, and say so in the build.
val serverJavaVersion = if (mcVersionString.startsWith("26")) 25 else 21
val javaToolchains = extensions.getByType<JavaToolchainService>()

val runServerAction = Action<RunServer> {
    version(mcVersionString)
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(serverJavaVersion)
    }
}

runPaper.folia.registerTask(op = runServerAction)

tasks {
    runServer {
        runServerAction.execute(this)
    }

    jar {
        finalizedBy(shadowJar)
    }

    shadowJar {
        archiveClassifier = ""

        dependencies {
            exclude(dependency("org.jetbrains:annotations"))
        }

        relocate("org.bstats", "$groupString.shaded.org.bstats")
    }
}
