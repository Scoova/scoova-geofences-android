plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
    `maven-publish`
    signing
}

group = "info.scoo-va"
version = "1.0.3"

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
    withJavadocJar()
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

            groupId    = "info.scoo-va"
            artifactId = "scoova-geofences-android"
            version    = project.version.toString()

            pom {
                name.set("Scoova Geofences SDK (Android / JVM)")
                description.set("Geofence CRUD + point-in-fence containment check.")
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
        // GitHub Packages — works immediately in Actions via GITHUB_TOKEN.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Scoova/scoova-geofences-android")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user") as? String ?: ""
                password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key") as? String ?: ""
            }
        }

        // Local staging dir. `publishReleasePublicationToLocalStagingRepository`
        // writes the signed Maven layout here; the publish-to-central-portal.sh
        // script zips it and uploads to Sonatype Central Portal.
        maven {
            name = "LocalStaging"
            url = uri(layout.buildDirectory.dir("staging-deploy"))
        }
    }
}

// In-memory PGP signing — required by Maven Central. SIGNING_KEY is the
// ASCII-armored secret key; SIGNING_PASSWORD is optional (current Scoova
// release key is passphrase-less). When absent (local builds, GitHub
// Packages), signing is skipped.
signing {
    val signingKey: String? = System.getenv("SIGNING_KEY")
    val signingPassword: String = System.getenv("SIGNING_PASSWORD") ?: ""
    isRequired = signingKey != null
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["release"])
    }
}