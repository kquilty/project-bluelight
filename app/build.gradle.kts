plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.projectbluelight"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.projectbluelight"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    // Jetpack Glance — the modern way to build home-screen widgets.
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // For the permission screen (MainActivity) and the coroutine that refreshes the widget.
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
}
