// 版本口径见 docs/开发规范-v1.11.md §2.2 / §13.1
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    id("com.android.application") version "8.7.2" apply false
    id("com.android.library") version "8.7.2" apply false
    kotlin("android") version "2.0.21" apply false
    kotlin("plugin.compose") version "2.0.21" apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.27" apply false
    // 静态检查与快照：ktlint ≥1.3 / detekt ≥1.23 / Roborazzi 锁定 1.x（规范 §13.1）
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.7" apply false
    id("io.github.takahirom.roborazzi") version "1.13.0" apply false
}

// ktlint / detekt 对全部 Android 子模块（app / core-ui / core-data / ocr）统一生效，
// 使 CI 根级 `gradle ktlintCheck detekt` 聚合所有模块，而非只检查单个模块（规范 §2.1 / §13 / §13.1）。
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Tokens.kt 由 tools/gen_tokens.py 生成（数值事实源是 tokens.json），
    // 不参与风格检查与自动格式化，避免生成物与 --check 漂移。
    extensions.configure<KtlintExtension>("ktlint") {
        filter { exclude("**/tokens/Tokens.kt") }
    }
    extensions.configure<DetektExtension>("detekt") {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }
    tasks.withType<Detekt>().configureEach {
        exclude("**/tokens/Tokens.kt")
    }
}
