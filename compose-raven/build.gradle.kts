plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("maven-publish")
    id("signing")
}

android {
    namespace  = "io.github.dracovin.composeraven"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.startup.runtime)
    implementation(libs.coroutines.android)
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.3")
    implementation("androidx.savedstate:savedstate:1.2.1")
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.manifest)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId    = "io.github.dracovin"
                artifactId = "compose-raven"
                version    = "0.1.0-alpha01"

                pom {
                    name        = "composeRaven"
                    description = "Zero-boilerplate in-app UI inspector & debug overlay for Jetpack Compose"
                    url         = "https://github.com/dracovin/compose-raven"
                    licenses {
                        license {
                            name = "Apache-2.0"
                            url  = "https://www.apache.org/licenses/LICENSE-2.0"
                        }
                    }
                    developers {
                        developer {
                            id    = "dracovin"
                            name  = "dracovin"
                            email = "siddhardha.d@kynhood.com"
                        }
                    }
                    scm {
                        connection          = "scm:git:git://github.com/dracovin/compose-raven.git"
                        developerConnection = "scm:git:ssh://github.com/dracovin/compose-raven.git"
                        url                 = "https://github.com/dracovin/compose-raven"
                    }
                }
            }
        }

        repositories {
            maven {
                name = "Local"
                url  = uri(layout.buildDirectory.dir("local-repo"))
            }
            maven {
                name = "MavenCentral"
                url  = uri(
                    if (version.toString().endsWith("SNAPSHOT"))
                        "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                    else
                        "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                )
                credentials {
                    username = providers.gradleProperty("ossrhUsername").orNull
                    password = providers.gradleProperty("ossrhPassword").orNull
                }
            }
        }
    }

    signing {
        val keyId      = providers.gradleProperty("signing.keyId").orNull
        val password   = providers.gradleProperty("signing.password").orNull
        val secretRing = providers.gradleProperty("signing.secretKeyRingFile").orNull
        if (keyId != null && password != null && secretRing != null) {
            useInMemoryPgpKeys(file(secretRing).readText(), password)
            sign(publishing.publications["release"])
        }
    }
}
