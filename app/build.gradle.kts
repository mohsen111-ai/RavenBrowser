plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GeckoView ships one artifact per CPU architecture. Phones use arm64-v8a; emulators use x86_64.
val geckoAbi = (findProperty("geckoAbi") as String?) ?: "arm64-v8a"
val geckoVersion = "157.0.20260924084938"
val buildNumber = (System.getenv("RAVEN_BUILD") ?: "1").toInt()
// Speed tests only: a copy of Raven with one part switched off, installed next to Raven ("nohelper", "notranslate").
val speedVariant = (findProperty("speedVariant") as String?).orEmpty()

android {
    namespace = "app.raven.browser"
    compileSdk {
        version = release(37) { minorApiLevel = 1 }
    }

    defaultConfig {
        applicationId = "app.raven.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        ndk { abiFilters += geckoAbi }
        buildConfigField("String", "GECKO_VERSION", "\"$geckoVersion\"")
        buildConfigField("String", "SPEED_VARIANT", "\"$speedVariant\"")
        if (speedVariant.isNotEmpty()) applicationIdSuffix = ".$speedVariant"
    }

    signingConfigs {
        // CI signs with a stable key from repository secrets when they exist; otherwise the debug key.
        val keystore = System.getenv("RAVEN_KEYSTORE_FILE")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("RAVEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RAVEN_KEY_ALIAS")
                keyPassword = System.getenv("RAVEN_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // Keep native libraries compressed in the APK: smaller download, extracted on install.
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Screen pictures (src/test/.../Shots.kt) are drawn on the computer, without a phone; they land in
        // app/build/outputs/roborazzi.
        unitTests.all { it.systemProperty("roborazzi.test.record", "true") }
    }
}

dependencies {
    implementation("org.mozilla.geckoview:geckoview-$geckoAbi:$geckoVersion")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.biometric:biometric:1.1.0")
    // Biometric brings fragment 1.2.5, which rejects the request codes that permission and result requests use now
    // ("Can only use lower 16 bits for requestCode"); a current fragment fixes that.
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    // Raven's own VPN: WireGuard (the protocol Proton VPN uses), only for Raven's traffic.
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.52.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.52.0")
    testImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
