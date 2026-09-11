import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
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
    namespace = "com.lw.top.lib_core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        val douyinAppId = if (packagingEnabled) cfg("DOUYIN_APP_ID", "") else ""
        val douyinAppName = if (packagingEnabled) cfg("DOUYIN_APP_NAME", "") else ""
        buildConfigField("String", "DOUYIN_APP_ID", "\"$douyinAppId\"")
        buildConfigField("String", "DOUYIN_APP_NAME", "\"$douyinAppName\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "USE_MOCK_API", "false")
        }
        getByName("debug") {
            buildConfigField("boolean", "USE_MOCK_API", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }
}

dependencies {
    api(libs.androidx.core.ktx)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.androidx.activity.compose)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.ui)
    api(libs.androidx.ui.graphics)
    api(libs.androidx.ui.tooling.preview)
    api(libs.androidx.material3)
    api(libs.androidx.material3.icons)
    api(libs.androidx.core.splashscreen)
    api(libs.androidx.runtime.livedata)
    api(libs.androidx.navigation.compose)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.appcompat)

    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)

    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.retrofit.gson)
    api(libs.gson)

    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    ksp(libs.room.compiler)

    api(libs.utilcodex)

    api(libs.bundles.coil)
    api(libs.bundles.media3)

    api(libs.androidx.datastore.preferences)
    api(libs.androidx.preference)

    api(libs.webkit)
    api(libs.libpag)

    api(libs.rxjava3)
    api(libs.rxandroid)
    api(libs.rxandroidble)

    api(libs.instavision.ffmpeg)
    api(libs.volcengine.douyin.sdk)
    api(libs.douyin.open.sdk.common)
    api(libs.douyin.open.sdk.china.external)

    testApi(libs.junit)
    androidTestApi(libs.androidx.junit)
    androidTestApi(libs.androidx.espresso.core)
    androidTestApi(platform(libs.androidx.compose.bom))
    androidTestApi(libs.androidx.ui.test.junit4)
    debugApi(libs.androidx.ui.tooling)
    debugApi(libs.androidx.ui.test.manifest)
}
