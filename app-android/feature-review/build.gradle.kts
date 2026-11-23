plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.chenyubang.feature.review"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// 依赖方向硬规则（规范 §3 / §4.4）：
//   feature → core-ui / core-data / ocr 是允许的唯一方向；
//   feature 之间不得互相依赖；core-ui 不得依赖任何 feature。
// ktlint / detekt 由根 build.gradle.kts 的 subprojects 统一 apply，本模块不另起一套。
dependencies {
    api(project(":core-ui"))
    implementation(project(":core-data"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.13.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}

roborazzi {
    outputDir.set(file("src/test/snapshots"))
}
