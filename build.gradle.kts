import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile

plugins {
    kotlin("multiplatform") version "1.3.31"
    id("maven-publish")
    signing
    id("org.jetbrains.dokka") version "0.9.18"
}

repositories {
    mavenCentral()
    maven("https://dl.bintray.com/kotlin/dokka")
    maven("https://jitpack.io")
}

group = "io.github.gciatto"
version = "0.0.3"

kotlin {

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }

        // Default source set for JVM-specific sources and dependencies:
        jvm {
            compilations["main"].defaultSourceSet {
                dependencies {
                    implementation(kotlin("stdlib-jdk8"))
                }
            }

            mavenPublication {
                artifactId = rootProject.name + "-jvm"
            }

            // JVM-specific tests and their dependencies:
            compilations["test"].defaultSourceSet {
                dependencies {
                    implementation(kotlin("test-junit"))
                }
            }
        }

        js {
            sequenceOf("", "Test").forEach {
                tasks.getByName<KotlinJsCompile>("compile${it}KotlinJs") {
                    kotlinOptions {
                        moduleKind = "umd"
                        noStdlib = true
                        metaInfo = true
                    }
                }
            }
            compilations["main"].defaultSourceSet {
                dependencies {
                    implementation(kotlin("stdlib-js"))
                }
            }
            compilations["test"].defaultSourceSet {
                dependencies {
                    implementation(kotlin("test-js"))
                }
            }

            mavenPublication {
                artifactId = rootProject.name + "-js"
            }
        }
    }
}

val docDir = "$buildDir/doc"

tasks.withType<DokkaTask> {

    outputDirectory = docDir
    jdkVersion = 8
    reportUndocumented = false
//    outputFormat = "javadoc"

    kotlinTasks {
        listOf()
    }

    sourceRoot {
        // assuming there is only a single source dir...
        kotlin.sourceSets.commonMain {
            this@sourceRoot.path = kotlin.srcDirs.first().absolutePath
        }
        platforms = listOf("Common")
    }

    sourceRoot {
        // assuming there is only a single source dir...
        with(kotlin.sourceSets.get("jvmMain")) {
            this@sourceRoot.path = kotlin.srcDirs.first().absolutePath
        }
        platforms = listOf("JVM")
    }

    sourceRoot {
        // assuming there is only a single source dir...
        with(kotlin.sourceSets.get("jsMain")) {
            this@sourceRoot.path = kotlin.srcDirs.first().absolutePath
        }
        platforms = listOf("JS")
    }
}

fun capitalize(s: String): String {
    return s[0].toUpperCase() + s.substring(1)
}

val jarPlatform = tasks.withType<Jar>().map { it.name.replace("Jar", "") }

task<DefaultTask>("packAllDokka") {
    group = "documentation"
}

//tasks.getByName("signArchives").dependsOn("packAllDokka")

jarPlatform.forEach {
    val packDokkaForPlatform = "packDokka${capitalize(it)}"

    task<Jar>(packDokkaForPlatform) {
        group = "documentation"
        dependsOn("dokka")
        from(docDir)
        archiveBaseName.set(rootProject.name)
        archiveVersion.set(rootProject.version.toString())
        archiveAppendix.set(it)
        archiveClassifier.set("javadoc")
    }

    tasks.getByName("${it}Jar").dependsOn(packDokkaForPlatform)
    tasks.getByName("packAllDokka").dependsOn(packDokkaForPlatform)
}

// https://central.sonatype.org/pages/requirements.html
// https://docs.gradle.org/current/userguide/signing_plugin.html
publishing {
    publications.withType<MavenPublication> {

        groupId = rootProject.group.toString()
        version = rootProject.version.toString()

        val docArtifact = "packDokka${capitalize(name)}"

        if (docArtifact in tasks.names) {
            artifact(tasks.getByName(docArtifact)) {
                classifier = "javadoc"
            }
        }

        pom {
            name.set("Kotlin Math")
            description.set("Pure Kotlin porting of the java.math package")
            url.set("https://github.com/gciatto/kt-math")
            licenses {
                license {
                    name.set("GNU General Public License, version 2, with the Classpath Exception")
                    url.set("https://openjdk.java.net/legal/gplv2+ce.html")
                }
            }

            developers {
                developer {
                    email.set("giovanni.ciatto@gmail.com")
                    url.set("https://about.me/gciatto")
                    organization.set("GitHub")
                    organizationUrl.set("https://github.com")
                }
            }

            scm {
                connection.set("scm:git:git:///github.com/gciatto/kt-math.git")
                developerConnection.set("scm:git:ssh://github.com:gciatto/kt-math.git")
                url.set("https://github.com/gciatto/kt-math")
            }
        }

    }

    repositories {

        val mavenRepoUrl = if (version.toString().contains("SNAPSHOT")) {
            "https://oss.sonatype.org/content/repositories/snapshots/"
        } else {
            "https://oss.sonatype.org/service/local/staging/deploy/maven2/"
        }

        maven(mavenRepoUrl) {
            credentials {
                username = rootProject.property("ossrhUsername").toString()
                password = rootProject.property("ossrhPassword").toString()
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications)
}

publishing {
    val pubs = publications.withType<MavenPublication>().map{ "sign${capitalize(it.name)}Publication" }

    task<Sign>("signAllPublications") {
        dependsOn(*pubs.toTypedArray())
    }
}