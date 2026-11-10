import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val signingPropertiesFile = rootProject.file("keystore.properties")
val signingProperties = Properties().apply {
    if (signingPropertiesFile.exists()) {
        signingPropertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    signingProperties.getProperty(propertyName)
        ?.takeIf { it.isNotBlank() }
        ?: System.getenv(environmentName)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.sipun.downly"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.sipun.downly"
        minSdk = 30
        targetSdk = 37
        versionCode = 12
        versionName = "1.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            excludes += setOf(
                "**/libpython.zip.so",
                "**/libffmpeg.zip.so",
                "**/libaria2c.zip.so"
            )
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = signingValue("storeFile", "ANDROID_KEYSTORE_FILE")
            val storePass = signingValue("storePassword", "ANDROID_KEYSTORE_PASSWORD")
            val alias = signingValue("keyAlias", "ANDROID_KEY_ALIAS")
            val keyPass = signingValue("keyPassword", "ANDROID_KEY_PASSWORD")

            if (keystorePath != null) storeFile = rootProject.file(keystorePath)
            if (storePass != null) storePassword = storePass
            if (alias != null) keyAlias = alias
            if (keyPass != null) keyPassword = keyPass
        }
    }

    buildTypes {
        debug {
            // Keep Android's normal debug signing.
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.coil.compose)
    implementation(libs.lottie.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)

    implementation("io.github.darkstar085.youtubedl-kotlin:library:1.0.2")
    implementation("io.github.darkstar085.youtubedl-kotlin:ffmpeg:1.0.2")
    implementation("io.github.darkstar085.youtubedl-kotlin:aria2c:1.0.2")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
