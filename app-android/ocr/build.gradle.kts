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
    // Paddle-Lite 2.10 Java 源码随包（src/main/java/com/baidu/paddle/lite），JNI .so 在 jniLibs/arm64-v8a；
    // 必须同时带 libc++_shared.so，否则 dlopen libpaddle_lite_jni.so 失败 → 每次识别空结果。
    // 模型为官方 PP-OCRv3 det/rec .nb（opt v2.10），与 so 版本严格一致；在 assets/ocr 下。版本口径见 docs/开发规范-v1.6.md 第 13 节。
    testImplementation("junit:junit:4.13.2")
}
