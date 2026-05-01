plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.baselineprofile)
    alias(libs.plugins.detekt)
    alias(libs.plugins.spotless)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.smoothradio.radio"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.smoothradio.radio"
        minSdk = 25
        targetSdk = 36
        versionCode = 29
        versionName = "3.7.6"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        compose = true
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

tasks.register("codeQualityCheck") {
    group = "verification"
    description = "Runs all code quality checks"
    dependsOn("lintDebug", "detekt", "spotlessCheck")
}

tasks.register("formatCode") {
    group = "formatting"
    description = "Formats code using Spotless"
    dependsOn("spotlessApply")
}

tasks.register("qualityAndFormat") {
    group = "verification"
    description = "Formats code then runs quality checks"
    dependsOn("spotlessApply", "codeQualityCheck")
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.core.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.media)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.compose.material)
    detektPlugins(libs.detekt.formatting)
    implementation(libs.timber)
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Third party libraries
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.lottie)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
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
    ksp(libs.hilt.compiler)
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
