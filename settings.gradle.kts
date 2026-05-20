pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.repsy.io/mvn/linwear/android") }
        maven { url = uri("https://artifact.bytedance.com/repository/AwemeOpenSDK") }

        maven {
            url = uri("https://artifact.bytedance.com/repository/encop_and_sol_ai_product/")
        }

        maven {
            url = uri("https://artifact.bytedance.com/repository/Volcengine/")
        }

        maven { url = uri("https://maven.zego.im") }
    }
}

rootProject.name = "LinWearAiGlasses"
include(":app")
include(":lib_core")
