import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val testMailConfigKeys = listOf(
    "test.mail.imap.host",
    "test.mail.imap.port",
    "test.mail.smtp.host",
    "test.mail.smtp.port",
    "test.mail.smtp.starttls",
    "test.mail.email",
    "test.mail.password",
    "test.mail.recipient",
    "SHARED_SECRET"
)

val testMailConfig: Map<String, String> = run {
    val props = Properties()
    // Layer .env.test on top of .env so partial overrides don't blank base keys.
    rootProject.file(".env").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    rootProject.file(".env.test").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
    testMailConfigKeys.associateWith { key ->
        System.getenv(key.uppercase().replace('.', '_')) ?: props.getProperty(key, "")
    }
}.also { cfg ->
    println("[e2e-diag] SHARED_SECRET baked length = ${cfg["SHARED_SECRET"]?.length ?: 0}")
}

val appVersionName = System.getenv("VERSION_NAME") ?: "0.0.1"
val appSemverParts = appVersionName.split(".")
val appMajor = appSemverParts.getOrNull(0)?.toIntOrNull() ?: 0
val appMinor = appSemverParts.getOrNull(1)?.toIntOrNull() ?: 0
val appPatch = appSemverParts.getOrNull(2)?.toIntOrNull() ?: 1
val appVersionCode = appMajor * 1_000_000 + appMinor * 1_000 + appPatch

val signingKeystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
val signingKeystorePassword = System.getenv("KEYSTORE_PASSWORD")?.takeIf { it.isNotBlank() }
val signingKeyAlias = System.getenv("KEY_ALIAS")?.takeIf { it.isNotBlank() }
val signingKeyPassword = System.getenv("KEY_PASSWORD")?.takeIf { it.isNotBlank() }
val signingKeystoreFile = signingKeystorePath
    ?.let { rootProject.file(it).absoluteFile }
    ?.takeIf { it.isFile }
val hasReleaseSigningConfig = signingKeystoreFile != null &&
    signingKeystorePassword != null &&
    signingKeyAlias != null &&
    signingKeyPassword != null

android {
    namespace = "com.cocode.claudeemailapp"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.cocode.claudeemailapp"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testMailConfig.forEach { (k, v) ->
            if (v.isNotBlank()) testInstrumentationRunnerArguments[k] = v
        }
    }

    signingConfigs {
        if (hasReleaseSigningConfig) {
            create("release") {
                storeFile = signingKeystoreFile!!
                storePassword = signingKeystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            enableAndroidTestCoverage = true
        }
        release {
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                it.systemProperties(testMailConfig.filterValues { v -> v.isNotBlank() })
            }
        }
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "META-INF/NOTICE.markdown",
                "META-INF/LICENSE.markdown",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.angus.mail)
    implementation(libs.angus.activation)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val coverageExclusions = listOf(
    "**/R.class", "**/R$*.class",
    "**/BuildConfig.*", "**/Manifest*.*",
    "**/*_Factory*.*", "**/*_MembersInjector*.*",
    "**/ui/theme/**",
    "**/*ComposableSingletons*.*",
    "**/*\$*Composable*.*",
    "**/*\$\$inlined*.*",
    "**/*\$\$Lambda*.*",
    "**/*Kt\$*.*",
    "**/MainActivity*.*"
)

tasks.register<JacocoReport>("jacocoDebugReport") {
    group = "verification"
    description = "Generates Jacoco coverage report for the debug build (unit + androidTest)."

    dependsOn("testDebugUnitTest", "createDebugCoverageReport")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val javaClasses = fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
        exclude(coverageExclusions)
    }
    val kotlinClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
        exclude(coverageExclusions)
    }
    classDirectories.setFrom(files(javaClasses, kotlinClasses))

    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))

    executionData.setFrom(
        fileTree(layout.buildDirectory) {
            include(
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
                "outputs/code_coverage/debugAndroidTest/connected/**/*.ec"
            )
        }
    )
}
