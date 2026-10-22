plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.pickwords"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.pickwords"
        minSdk = 29
        targetSdk = 35
        versionCode = 3
        versionName = "3.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
