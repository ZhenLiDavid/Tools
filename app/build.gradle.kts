import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

val localProperties = Properties().apply {
    rootProject.file("local.properties")
        .takeIf { it.isFile }
        ?.inputStream()
        ?.use { load(it) }
}
val firebaseTesters = providers.gradleProperty("firebaseTesters").orNull
    ?: localProperties.getProperty("firebaseTesters")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zhentech.tools"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zhentec.tools"
        minSdk = 31
        targetSdk = 36
        versionCode = 4
        versionName = "1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    lint {
        disable += "OldTargetApi"
    }

    buildTypes {
        debug {
            firebaseAppDistribution {
                artifactType = "APK"
                releaseNotes = providers.gradleProperty("firebaseReleaseNotes")
                    .orElse("Private development build")
                    .get()
                firebaseTesters
                    ?.takeIf(String::isNotBlank)
                    ?.let { testers = it }
            }
        }
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
