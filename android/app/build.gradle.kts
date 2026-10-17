import com.android.build.gradle.internal.api.BaseVariantOutputImpl

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing is configured only when all four variables are set.
// Without them the release build stays unsigned and the build does not fail.
val releaseKeystore: String? = System.getenv("CATFINDER_KEYSTORE")
val releaseKeystorePassword: String? = System.getenv("CATFINDER_KEYSTORE_PASSWORD")
val releaseKeyAlias: String? = System.getenv("CATFINDER_KEY_ALIAS")
val releaseKeyPassword: String? = System.getenv("CATFINDER_KEY_PASSWORD")
val releaseSigningAvailable = listOf(
    releaseKeystore, releaseKeystorePassword, releaseKeyAlias, releaseKeyPassword,
).all { !it.isNullOrBlank() }

// The release workflow passes -PappVersion=X.Y.Z from the git tag.
val appVersion = (findProperty("appVersion") as String?) ?: "1.0.0"
val appVersionCode = appVersion.split(".").map { it.toIntOrNull() ?: 0 }
    .let { (it + listOf(0, 0, 0)).take(3) }
    .let { (major, minor, patch) -> major * 10000 + minor * 100 + patch }

android {
    namespace = "com.catfinder.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.catfinder.app"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    // app/build/outputs/apk/<type>/CatFinder.apk instead of app-<type>.apk
    applicationVariants.all {
        outputs.all { (this as BaseVariantOutputImpl).outputFileName = "CatFinder.apk" }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
