plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.smoothradio.radio"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        minSdk = 28
        targetSdk = 36

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "LOW-BATTERY,DEBUGGABLE"
    }

    targetProjectPath = ":app"

    // This code creates the gradle managed device used to generate baseline profiles.
    // To use GMD please invoke generation through the command line:
    // ./gradlew :app:generateBaselineProfile
    testOptions {
        managedDevices {
            localDevices {
                create("pixel2api30") {
                    // Use device profiles you typically see in Android Studio.
                    device = "Pixel 2"
                    // ATDs currently support only API level 30.
                    apiLevel = 30
                    // You can also specify "google-atd" if you require Google Play Services.
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

// This is the configuration block for the Baseline Profile plugin.
// You can specify to run the generators on a managed devices or connected devices.
baselineProfile {
    managedDevices += "pixel2api30"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map { artifactsLoader.load(it)?.applicationId }
        )
    }
}