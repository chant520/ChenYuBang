pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ChenYuBang"

// 骨架与页面归属见 docs/开发规范-v1.6.md §3：
// feature 按页面簇切，feature 之间不得互相依赖（规范 §3 硬规则）。
include(
    ":app",
    ":core-ui",
    ":core-data",
    ":ocr",
    ":feature-idiom",
    ":feature-example",
    ":feature-mistake",
    ":feature-review",
    ":feature-me",
    ":feature-capture"
)
