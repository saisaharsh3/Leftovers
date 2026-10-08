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
        versionCode = 14
        versionName = "1.4.0"
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
        // Test builds install next to the real app (as "Leftovers Dev") instead of replacing it.
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            // Signed with the release key when it's available (local builds, and CI builds of dev), so each
            // new Leftovers Dev installs over the last one instead of failing with "App not installed".
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // No git commit stamp in the APK, so builds of the same code always match byte for byte.
            vcsInfo.include = false
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

    // Leave out the encrypted dependency list Google Play reads. F-Droid rejects it, and it keeps
    // the APK identical whoever builds it (so F-Droid can ship our signed release).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            // The Jakarta Mail jars each carry the same licence files (credited in THIRD_PARTY_NOTICES.md).
            // Their META-INF/javamail.* provider lists are kept: the library needs them at run time.
            excludes += setOf("META-INF/NOTICE.md", "META-INF/LICENSE.md", "META-INF/NOTICE", "META-INF/LICENSE")
        }
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
    // A throwaway in-memory mail server for testing email detection.
    testImplementation(libs.greenmail)
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
    // Email payment detection (IMAP over TLS). Only used when the user connects a mailbox.
    implementation(libs.angus.mail)
}
