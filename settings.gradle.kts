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
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven(url = "https://jitpack.io")
    }
}

rootProject.name = "SantiyaLocalAiHub"
include(":app")
include(":santiya-localai-sdk")
include(":memory-vault")
include(":neuron-packet")
include(":system_encryptor")
include(":file_ops")
include(":ums")
include(":distributed-gguf-runtime")
include(":google-local-runtime")
include(":desktop-app")
