plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.crash2test"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.1.1")
        bundledPlugin("com.intellij.java")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }

    testImplementation(kotlin("test"))
    testRuntimeOnly("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    runIde {
        args = listOf(project.projectDir.absolutePath)
    }
}
