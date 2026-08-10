plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.liftly.app.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.liftly.app.wear"
        minSdk = 30
        targetSdk = 36
        versionCode = 39
        versionName = "1.6.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")
    implementation("androidx.wear.compose:compose-material3:1.6.2")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.health:health-services-client:1.1.0-rc02")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
