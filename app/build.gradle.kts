plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Permanent signing key. CI restores it from GitHub secrets into a temp path; locally it can also
// sit in signing/ (that folder is git-ignored). Without a key the release build stays unsigned and
// the workflow falls back to the debug APK, so nothing breaks for contributors.
val stableStore: String? = System.getenv("SIGNING_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() && file(it).exists() }
    ?: rootProject.file("signing/utouch-launcher.p12")
        .takeIf { it.exists() }
        ?.absolutePath

val stablePassword: String? = System.getenv("SIGNING_STORE_PASSWORD")
    ?: rootProject.file("signing/password.txt")
        .takeIf { it.exists() }
        ?.readText()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

val canSignRelease = stableStore != null && stablePassword != null

android {
    namespace = "dev.utouch.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.utouch.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.5.1"
        resourceConfigurations += listOf("en", "ru")
    }

    signingConfigs {
        if (canSignRelease) {
            create("stable") {
                storeFile = file(stableStore!!)
                storePassword = stablePassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "utouch"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: stablePassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("stable")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
}
