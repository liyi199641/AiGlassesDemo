import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
}

val packagingProps: Properties = Properties().also { props ->
    val file = rootProject.file("douyin.properties")
    if (file.exists()) {
        FileInputStream(file).use { props.load(it) }
    }
}

fun cfg(key: String, default: String): String =
    packagingProps.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() } ?: default

val packagingEnabled: Boolean = cfg("CONFIG_ENABLED", "false").toBoolean()

android {
    namespace = "com.lw.ai.glasses"
    compileSdk = 36

    signingConfigs {
        if (packagingEnabled) {
            getByName("debug") {
                storeFile = rootProject.file(cfg("KEY_STORE_FILE", ""))
                storePassword = cfg("KEY_STORE_PASSWORD", "")
                keyAlias = cfg("KEY_ALIAS", "")
                keyPassword = cfg("KEY_PASSWORD", "")
                enableV1Signing = true
                enableV2Signing = true
            }
            create("release") {
                storeFile = rootProject.file(cfg("KEY_STORE_FILE", ""))
                storePassword = cfg("KEY_STORE_PASSWORD", "")
                keyAlias = cfg("KEY_ALIAS", "")
                keyPassword = cfg("KEY_PASSWORD", "")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = if (packagingEnabled) cfg("APPLICATION_ID", "com.lw.ai.glasses") else "com.lw.ai.glasses"

        val douyinClientKey = if (packagingEnabled) cfg("DOUYIN_CLIENT_KEY", "") else ""
        val douyinClientSecret = if (packagingEnabled) cfg("DOUYIN_CLIENT_SECRET", "") else ""
        buildConfigField("String", "DOUYIN_CLIENT_KEY", "\"$douyinClientKey\"")
        buildConfigField("String", "DOUYIN_CLIENT_SECRET", "\"$douyinClientSecret\"")
        minSdk = 26
        targetSdk = 36
        versionCode = (System.currentTimeMillis() / 1000).toInt()
        versionName = "1.0"

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            if (packagingEnabled) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        release {
            signingConfig = if (packagingEnabled) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
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

    kotlin {
        jvmToolchain(11)
    }

    packaging {
        jniLibs {
            pickFirsts += setOf(
                "lib/armeabi-v7a/libc++_shared.so",
                "lib/arm64-v8a/libc++_shared.so",
                "lib/x86/libc++_shared.so",
                "lib/x86_64/libc++_shared.so"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.play.services.vision.common)
    implementation("androidx.documentfile:documentfile:1.1.0")
    ksp(libs.hilt.android.compiler)
    implementation(project(":lib_core"))
    implementation(libs.glasses.sdk.core)
    implementation(libs.glasses.sdk.ly)
    implementation(libs.glasses.sdk.rtk)
    implementation(libs.glasses.sdk.tb)
    implementation(libs.bundles.media3)
    coreLibraryDesugaring(libs.android.desugarJdkLibs)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
}
