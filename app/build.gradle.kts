import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing comes from `keystore.properties` (local builds) or environment variables (CI).
 * Neither is committed; without them the release build is simply left unsigned.
 */
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signingValue(key: String, env: String): String? = signing.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.leftovers.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.leftovers.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.1.0"
    }

    signingConfigs {
        val storePath = signingValue("storeFile", "SIGNING_STORE_FILE")
        if (storePath != null) {
            create("release") {
                storeFile = rootProject.file(storePath)
                storePassword = signingValue("storePassword", "SIGNING_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "SIGNING_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    // Database upgrade tests run on the JVM (Robolectric), which reads the debug build's assets;
    // the schemas go there so release builds stay free of them.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    sourceSets {
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    testImplementation(libs.junit)
    // Real org.json for unit tests (the Android stub only throws on the JVM).
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(libs.haze)
    implementation(libs.haze.blur)

    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.exifinterface)
}
