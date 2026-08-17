import java.util.Base64

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.nmcp)
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
    publishing {
        singleVariant("release")
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.startup.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.savedstate)
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.manifest)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId    = "io.github.dracovin"
            artifactId = "compose-raven"
            version    = "0.1.0-alpha02"

            afterEvaluate { from(components["release"]) }

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
                        email = "vinay.parampalli@gmail.com"
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
    }
}

signing {
    val password   = providers.gradleProperty("signing.password").orNull
                  ?: providers.gradleProperty("signingPassword").orNull
    val secretRing = providers.gradleProperty("signing.secretKeyRingFile").orNull
    val signingKey = providers.gradleProperty("signingKey").orNull

    val resolvedKey = when {
        signingKey  != null -> String(Base64.getMimeDecoder().decode(signingKey))
        secretRing  != null -> file(secretRing).readText()
        else                -> null
    }

    if (password != null && resolvedKey != null) {
        useInMemoryPgpKeys(resolvedKey, password)
        sign(publishing.publications["release"])
    }
}

nmcp {
    publish("release") {
        username = providers.gradleProperty("ossrhUsername").getOrElse("")
        password = providers.gradleProperty("ossrhPassword").getOrElse("")
        publicationType = "AUTOMATIC"
    }
}