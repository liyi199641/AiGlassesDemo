plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.parcelize)
}
android {
    namespace = "com.lw.top.lib_core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
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
            // 为 debug 构建类型设置 USE_MOCK_API 为 true
            buildConfigField("boolean", "USE_MOCK_API", "false")
            // 如果您想在 debug 构建中默认启用 mock，可以这样做
            // isDebuggable = true // debug 构建类型默认就是 debuggable
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    //compose bom
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

    //hilt
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)

    //network
    api(libs.okhttp)
    api(libs.okhttp.logging)
    api(libs.retrofit)
    api(libs.retrofit.gson)
    api(libs.gson)

    //room
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.room.paging)
    ksp(libs.room.compiler)

    //util
    api(libs.utilcodex)

    //coil
    api(libs.bundles.coil)
    api(libs.bundles.media3)

    //preferences
    api(libs.androidx.datastore.preferences)
    api(libs.androidx.preference)

    api(libs.webkit)

    api(libs.libpag)

    api(libs.rxjava3)
    api(libs.rxandroid)
    api(libs.rxandroidble)

    api(libs.libvlc)
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
