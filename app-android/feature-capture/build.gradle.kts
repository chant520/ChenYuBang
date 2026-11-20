plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
    id("io.github.takahirom.roborazzi")
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

    // 页面快照（Roborazzi）需要读取 Android 资源，规范 §11.1 / §13
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
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
    // P20 页面级状态容器 + synonyms JSON 应用层解析
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.13.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}

// 页面快照基线入库（规范 §11.1）：record 写入此目录、verify 与此目录比对。
roborazzi {
    outputDir.set(file("src/test/snapshots"))
}
