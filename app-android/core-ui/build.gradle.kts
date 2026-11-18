plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.compose")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
    id("io.github.takahirom.roborazzi")
}

android {
    namespace = "com.chenyubang.core.ui"
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

    // Roborazzi 截图需读取 Android 资源，规范 §13
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    api("androidx.compose.ui:ui")
    api("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")

    // Roborazzi 快照接入（规范 §2.1 / §13 / §13.1），版本与根 plugins 块一致
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-junit-rule:1.13.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.13.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-manifest")
}

// 构件黄金快照基线入库（规范 §11.1 / §13）：record 写入此目录、verify 与此目录比对。
roborazzi {
    outputDir.set(file("src/test/snapshots"))
}
