plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val kaotofaceKeystore = rootProject.file("tools/keystore/kaotoface.jks")
val kaotofaceStorePassword = System.getenv("KAOTOFACE_STORE_PASSWORD")
val kaotofaceKeyPassword = System.getenv("KAOTOFACE_KEY_PASSWORD")
val kaotofaceKeyAlias = System.getenv("KAOTOFACE_KEY_ALIAS") ?: "kaotoface"
val canSignRelease = kaotofaceKeystore.exists() && !kaotofaceStorePassword.isNullOrBlank()

android {
    namespace = "com.kaotoface.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kaotoface.app"
        minSdk = 26
        targetSdk = 36
        // Version policy: patch +1 each shipped iteration, carry at ten:
        // 0.0.2 -> 0.0.3 ... 0.0.9 -> 0.1.0 ... 0.9.9 -> 1.0.0.
        // versionCode always increments by one with each release.
        versionCode = 6
        versionName = "0.0.6"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (canSignRelease) {
                storeFile = kaotofaceKeystore
                storePassword = kaotofaceStorePassword
                keyAlias = kaotofaceKeyAlias
                keyPassword = kaotofaceKeyPassword ?: kaotofaceStorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/*.kotlin_module",
            )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.12.0")

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // On-device streaming speech recognition: English + Mandarin
    implementation("com.alphacephei:vosk-android:0.3.47")

    testImplementation("junit:junit:4.13.2")
}
