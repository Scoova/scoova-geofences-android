plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
}

group = "info.scoo-va"
version = "1.0.1"

repositories { mavenCentral() }

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin { jvmToolchain(17) }

java {
    withSourcesJar()
}

tasks.test { useJUnitPlatform() }

// ─── Publishing ─────────────────────────────────────────────────────────
//
// Three targets configured side-by-side. JitPack reads the repo with no
// extra config — just tag a version. GitHub Packages and Maven Central
// pick up credentials from env when actually publishing; without them
// the publish tasks are no-ops so day-to-day `gradle build` is unaffected.

publishing {
    publications {
        create<MavenPublication>("release") {
            from(components["java"])

            groupId = "info.scoo-va"
            artifactId = "geofences"
            version = project.version.toString()

            pom {
                name.set("Scoova Geofences (Android / JVM)")
                description.set("Geofences client for the Scoova platform — stored named polygons + point-in-polygon checks. Use it for service-area gating, no-parking zones, depot perimeters.")
                url.set("https://github.com/Scoova/scoova-geofences-android")

                licenses {
                    license {
                        name.set("Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("scoova")
                        name.set("Scoova")
                        email.set("info@scoo-va.info")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/Scoova/scoova-geofences-android.git")
                    developerConnection.set("scm:git:ssh://github.com:Scoova/scoova-geofences-android.git")
                    url.set("https://github.com/Scoova/scoova-geofences-android")
                }
            }
        }
    }

    repositories {
        // GitHub Packages — works immediately once GITHUB_TOKEN is set.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Scoova/scoova-geofences-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                    ?: project.findProperty("gpr.user") as? String ?: ""
                password = System.getenv("GITHUB_TOKEN")
                    ?: project.findProperty("gpr.key") as? String ?: ""
            }
        }

        // Maven Central staging.
        maven {
            name = "MavenCentral"
            val releasesUrl = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            val snapshotsUrl = uri("https://s01.oss.sonatype.org/content/repositories/snapshots/")
            url = if (version.toString().endsWith("SNAPSHOT")) snapshotsUrl else releasesUrl
            credentials {
                username = System.getenv("OSSRH_USERNAME")
                    ?: project.findProperty("ossrh.username") as? String ?: ""
                password = System.getenv("OSSRH_PASSWORD")
                    ?: project.findProperty("ossrh.password") as? String ?: ""
            }
        }
    }
}

// GPG signing — only enforced on the Maven Central publish task, so local
// builds and JitPack tags don't need GPG keys configured.
signing {
    isRequired = gradle.taskGraph.hasTask("publishReleasePublicationToMavenCentralRepository")
    sign(publishing.publications["release"])
}
