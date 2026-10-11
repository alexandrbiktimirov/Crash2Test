import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    kotlin("jvm") version "2.3.10"
    id("org.jetbrains.intellij.platform") version "2.14.0"
    jacoco
}

group = "com.crash2test"
version = providers.environmentVariable("PLUGIN_VERSION").orElse("0.1.0-SNAPSHOT").get()

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
        pluginVerifier()
        zipSigner()
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
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

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            current()
        }
    }
}

val writeSigningCertificate by tasks.registering {
    val certificate = providers.environmentVariable("CERTIFICATE_CHAIN")
    val outputFile = layout.buildDirectory.file("signing/certificate-chain.crt")
    inputs.property("certificateChain", certificate)
    outputs.file(outputFile)
    doLast {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(certificate.get())
        }
    }
}

tasks {
    verifyPluginSignature {
        // Gradle plugin 2.14.0 passes certificate content as an extra CLI argument; use a file instead.
        dependsOn(signPlugin, writeSigningCertificate)
        certificateChain.unsetConvention()
        certificateChainFile = layout.buildDirectory.file("signing/certificate-chain.crt")
    }

    test {
        useJUnitPlatform()
        extensions.configure(JacocoTaskExtension::class) {
            isIncludeNoLocationClasses = true
            excludes = listOf("jdk.internal.*")
        }
        finalizedBy(jacocoTestReport)
    }

    jacocoTestReport {
        dependsOn(test)
        classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
        reports {
            xml.required = true
            html.required = true
        }
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        classDirectories.setFrom(layout.buildDirectory.dir("instrumented/instrumentCode"))
        violationRules {
            rule {
                limit {
                    minimum = "0.80".toBigDecimal()
                }
            }
        }
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }

    runIde {
        coroutinesJavaAgentFile.set(layout.buildDirectory.file("disabled/coroutines-javaagent.jar"))
        systemProperty("kotlinx.coroutines.debug", "off")
        args = listOf(project.projectDir.absolutePath)
    }
}
