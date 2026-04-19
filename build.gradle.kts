// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

tasks.register("buildSmoke") {
    group = "verification"
    description = "Build debug APK, run unit tests, and run lint — used by CI and pre-commit."
    dependsOn(":app:assembleDebug", ":app:testDebugUnitTest", ":app:lintDebug")
}