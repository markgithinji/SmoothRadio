plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
}

android {
    namespace = "com.smoothradio.radio"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.smoothradio.radio"
        minSdk = 23
        targetSdk = 36
        versionCode = 17
        versionName = "2.10"
        testInstrumentationRunner = "com.smoothradio.radio.CustomTestRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"  // Enables simultaneous installation
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

detekt {
    toolVersion = "1.23.8"
    config = files("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
}

spotless {
    kotlin {
        target("**/*.kt")
        ktlint()
    }

    format("xml") {
        target("**/*.xml")
        prettier().config(mapOf("parser" to "xml"))
    }

    groovyGradle {
        target("*.gradle")
        greclipse()
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.media)
    detektPlugins(libs.detekt.formatting)
    implementation(libs.timber)
    // Third party libraries
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.lottie)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    // Ads
    implementation(libs.play.services.ads)
    implementation(libs.facebook)
    implementation(libs.user.messaging.platform) // For showing Regional Ad-Consent dialog
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.google.firebase.analytics)
    implementation(libs.google.firebase.firestore)
    // Unit Test
    testImplementation(libs.junit)
    testImplementation(kotlin("test"))
    testImplementation(libs.jetbrains.kotlinx.coroutines.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    // Instrumentation tests
    androidTestImplementation(libs.hilt.android.testing)
    kaptAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.uiautomator)
    debugImplementation(libs.androidx.espresso.intents) // set to debug impl due to known bugs
    debugImplementation(libs.androidx.fragment.testing) // set to debug impl due to known bugs


}
