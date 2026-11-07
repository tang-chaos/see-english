plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.codex.englishcamera"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.codex.englishcamera"
        minSdk = 26
        targetSdk = 36
        versionCode = 20
        versionName = "0.8.5"
        ndk { abiFilters += "arm64-v8a" }
        providers.gradleProperty("targetAbi").orNull?.let { abi -> ndk { abiFilters += abi } }
    }
    buildFeatures { compose = true;buildConfig=true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        noCompress += listOf("tflite", "onnx")
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.02.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("com.google.mediapipe:tasks-vision:0.10.32")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
