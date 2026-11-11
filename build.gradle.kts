
buildscript {
    dependencies {
        // AGP 9 proporciona Kotlin integrado; se mantiene KGP 2.4.21
        // porque el proyecto usa Kotlin stdlib/BOM 2.4.21.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.googleService) apply false
}
