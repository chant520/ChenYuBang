plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.chenyubang.ocr"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // 端侧 OCR 引擎（PaddleOCR mobile）未接入：模型与 AAR 需在 OCR 实测通过后落地，见规范 §2.3、§15
}
