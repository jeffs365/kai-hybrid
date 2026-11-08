plugins {
    alias(libs.plugins.androidApplication)
}

android {
    namespace = "com.inspiredandroid.kai.kokoro"
    compileSdk = 37
    ndkVersion = "26.1.10909125"
    defaultConfig {
        applicationId = "com.inspiredandroid.kai.kokoro"
        minSdk = 26
        targetSdk = 37
        versionCode = 20261108
        versionName = "1.12.22-kai.1"
        ndk { abiFilters += "arm64-v8a" }
    }
    sourceSets["main"].jniLibs.srcDir(".native")
    packaging { jniLibs { useLegacyPackaging = true } }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
