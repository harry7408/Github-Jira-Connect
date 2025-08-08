import org.jlleitschuh.gradle.ktlint.KtlintExtension

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ktlint) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    // 선택: 세부 옵션
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.7.0")      // ktlint CLI 엔진 버전 고정 (생략 가능)
        android.set(true)         // Android Kotlin 스타일 적용
        reporters {               // CI에서 읽기 쉬운 리포트 형식 추가
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
            reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        }
        filter {
            exclude("**/generated/**")
            include("**/*.kt", "**/*.kts")
        }
    }
}