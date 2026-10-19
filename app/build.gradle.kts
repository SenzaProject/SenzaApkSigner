plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.senza.apksigner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.senza.apksigner"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Ditandatangani dengan kunci debug otomatis supaya APK hasil CI langsung bisa dipasang.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/DEPENDENCIES",
                "META-INF/AL2.0", "META-INF/LGPL2.1", "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module", "DebugProbesKt.bin"
            )
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Mesin penandatanganan resmi Android (v1/v2/v3/v4) + verifier
    implementation("com.android.tools.build:apksig:8.9.1")
    // Zipalign (rata 4 byte / 16 KB untuk .so)
    implementation("org.apache.commons:commons-compress:1.27.1")
}
