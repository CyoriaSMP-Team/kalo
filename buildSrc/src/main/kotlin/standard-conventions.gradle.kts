plugins {
    id("java-library")
}

group = "io.kalo"
version = property("plugin_version").toString()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("org.projectlombok:lombok:1.18.44")
    annotationProcessor("org.projectlombok:lombok:1.18.44")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }

    test {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

// 1.21.4+ support: Java 21 baseline runs on both Paper 1.21.4 (Java 21) and 26.2 (Java 25).
// A Java 25-only jar would refuse to start on 1.21.4.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
