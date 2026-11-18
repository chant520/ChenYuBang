plugins {
    id("com.android.library")
    kotlin("android")
    kotlin("plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.chenyubang.core.data"
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

    // Room FTS 搜索 / 重建单测在 JVM 上跑，需 Robolectric 提供 Android SQLite 运行时（无 emulator）。
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

// Room schema 导出目录：版本演进要能 diff，见规范 §4.5.3
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // FTS 搜索 / 重建单测（JVM + Robolectric）。Room 需要真实 Android SQLite 运行时，
    // 本机无 emulator，故用 Robolectric 在 test 任务里跑；CI 的 `test` 任务同样执行。
    // runBlocking 复用 room-ktx 传递的 kotlinx-coroutines-core，不另引 coroutines-test。
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.13")
}
