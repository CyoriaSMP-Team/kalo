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

val runServerAction = Action<RunServer> {
    version(mcVersionString)
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
