plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.santiya.localaihub.sdk"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
