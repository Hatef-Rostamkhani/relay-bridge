plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.hatefrostamkhani.relaybridge"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.hatefrostamkhani.relaybridge"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.1-mvp"

        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
