// AGP 9 já traz o Kotlin embutido; o classpath abaixo fixa a versão do Kotlin
// para ficar igual à do plugin do compilador do Compose.
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.shadow) apply false
}
