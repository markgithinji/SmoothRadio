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

    compileSdk = 37

    defaultConfig {
        applicationId = "com.smoothradio.radio"
        minSdk = 25
        targetSdk = 37
        versionCode = 30
        versionName = "4.0.0"
        testInstrumentationRunner = "com.smoothradio.radio.CustomTestRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"  // Enables simultaneous installation
            isMinifyEnabled = false
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

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        getByName("test") {
            java.srcDirs("src/test/kotlin", "src/sharedTest/kotlin")
            kotlin.srcDirs("src/test/kotlin", "src/sharedTest/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java", "src/sharedTest/kotlin")
            kotlin.srcDirs("src/androidTest/java", "src/sharedTest/kotlin")
        }
    }

    configurations.all {
        resolutionStrategy {
            val coroutinesVersion = "1.11.0"
            force("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
            force("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:$coroutinesVersion")
        }
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
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.cast)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.compose.runtime.tracing)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    "baselineProfile"(project(":baselineprofile"))
    // Third party libraries
    implementation(libs.timber)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    detektPlugins(libs.detekt.formatting)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
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
    testImplementation(libs.androidx.junit)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Instrumentation tests
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    kspAndroidTest(libs.hilt.compiler)
    androidTestImplementation(libs.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockito.android)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
