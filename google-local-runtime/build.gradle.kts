plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.santiya.googlelocalruntime"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.litertlm)
    implementation(libs.google.mlkit.genai.prompt)
    implementation(libs.androidx.core.ktx)
}
