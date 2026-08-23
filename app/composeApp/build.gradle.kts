import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.lifecycle.viewmodel.compose)
            implementation(libs.navigation.compose)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.gitlive.firebase.auth)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.sqldelight.android.driver)
            implementation(libs.maplibre.android)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.goldenai.achievements"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goldenai.achievements"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${project.findProperty("API_BASE_URL") ?: "http://10.0.2.2:8000"}\"",
            )
            buildConfigField(
                "String",
                "MAP_STYLE_URL",
                "\"${project.findProperty("MAP_STYLE_URL") ?: "https://tiles.openfreemap.org/styles/bright"}\"",
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"${project.findProperty("API_BASE_URL") ?: "https://api.example.com"}\"",
            )
            buildConfigField(
                "String",
                "MAP_STYLE_URL",
                "\"${project.findProperty("MAP_STYLE_URL") ?: "https://tiles.openfreemap.org/styles/bright"}\"",
            )
        }
    }
}

sqldelight {
    databases {
        create("AchievementDatabase") {
            packageName.set("com.goldenai.achievements.db")
        }
    }
}

// Firebase config is per-developer and gitignored. Only wire up Google services
// when the config file is present so guest-mode builds work out of the box.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
}
