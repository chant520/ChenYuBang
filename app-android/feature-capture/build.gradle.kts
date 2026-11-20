plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
}

android {
    namespace = "com.chenyubang.feature.capture"
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

    buildFeatures {
        compose = true
    }
}

// 依赖方向硬规则（规范 §3 / §4.4）：
//   feature → core-ui / core-data / ocr 是允许的唯一方向；
//   feature 之间不得互相依赖；core-ui 不得依赖任何 feature。
// 本模块需要 :ocr —— 拍照链路 P17–P19 走端侧离线识别引擎（规范 §2.3）。
// ktlint / detekt 由根 build.gradle.kts 的 subprojects 统一 apply，本模块不另起一套。
dependencies {
    api(project(":core-ui"))
    implementation(project(":core-data"))
    implementation(project(":ocr"))

    // P17 取景与快门（规范 §13.1：CameraX 1.x）
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    // 运行时权限与 Photo Picker（系统图库选择器，不申请相册读取权限）
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // P18 读取相册/相机 JPEG 的 EXIF 方向，保证裁切前画面朝向正确
    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
