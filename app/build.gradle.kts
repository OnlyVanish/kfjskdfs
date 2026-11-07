import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

/* Optional push: put the four Firebase values from google-services.json into
 * gradle.properties (PINBOM_FCM_API_KEY, _APP_ID, _PROJECT_ID, _SENDER_ID) and the
 * app registers its FCM token with the backend. Without them everything still
 * builds and runs; the device simply is not registered for push. */
fun fcm(name: String) = providers.gradleProperty("PINBOM_FCM_$name").orElse("").get()
val fcmConfigured = listOf("API_KEY", "APP_ID", "PROJECT_ID", "SENDER_ID").all { fcm(it).isNotBlank() }

val releaseApiBaseUrl = providers.gradleProperty("PINBOM_API_BASE_URL").orElse("https://api.pinbom.example/").get()
require(releaseApiBaseUrl.startsWith("https://", ignoreCase = true) || releaseApiBaseUrl == "http://185.26.33.188:8080/") {
    "PINBOM_API_BASE_URL must use HTTPS for release builds"
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
    id("com.google.dagger.hilt.android")
}

val signingProperties = Properties().apply {
    val source = rootProject.file("keystore.properties")
    if (source.isFile) source.inputStream().use { load(it) }
}
fun signingValue(name: String): String = providers.gradleProperty(name)
    .orElse(providers.environmentVariable(name))
    .orElse(signingProperties.getProperty(name, "")).get()

android {
    signingConfigs {
        create("release") {
            storeFile = signingValue("PINBOM_STORE_FILE").takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
            storePassword = signingValue("PINBOM_STORE_PASSWORD")
            keyAlias = signingValue("PINBOM_KEY_ALIAS")
            keyPassword = signingValue("PINBOM_KEY_PASSWORD")
        }
    }
    namespace = "ir.pinbom.app"; compileSdk = 37
    defaultConfig { applicationId = "ir.pinbom.app"; minSdk = 24; targetSdk = 35; versionCode = 50808; versionName = "5.8.8"
        // Only the two shipped locales ride along: smaller APK, and a device set to
        // any other language falls back to values/ instead of a library's locale.
        resourceConfigurations += listOf("en", "fa")
        /* The C++ particle core (src/main/cpp). x86_64 keeps emulators working. */
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        buildConfigField("String", "FCM_API_KEY", "\"${fcm("API_KEY")}\"")
        buildConfigField("String", "FCM_APP_ID", "\"${fcm("APP_ID")}\"")
        buildConfigField("String", "FCM_PROJECT_ID", "\"${fcm("PROJECT_ID")}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${fcm("SENDER_ID")}\"")
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    /* Firebase-only sources are compiled solely when the FCM values above exist. */
    if (fcmConfigured) sourceSets.getByName("main").java.srcDir("src/firebase/java")
    /* Compose is several times slower in a debug build (no R8, no baseline profile,
     * debuggable runtime). Judge smoothness on `assembleRelease` only. The release
     * variant uses a dedicated release keystore from private keystore.properties
     * or PINBOM_* environment variables; never the Android debug key. */
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true; signingConfig = signingConfigs.getByName("release"); proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    buildFeatures { compose = true; buildConfig = true }
    /* Java and Kotlin must agree on a JVM target or kapt refuses to run. */
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildTypes.getByName("debug").buildConfigField("String", "DEFAULT_BASE_URL", "\"http://185.26.33.188:8080/\"")
    buildTypes.getByName("release").buildConfigField("String", "DEFAULT_BASE_URL", "\"$releaseApiBaseUrl\"")
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
    /* DIAGNOSTIC ONLY — lets a JVM (Robolectric) launch the real MainActivity. */
    testOptions { unitTests { isIncludeAndroidResources = true } }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

kapt { correctErrorTypes = true }

// نسخه‌های Compose را BOM هماهنگ می‌کند.
dependencies {
    implementation(platform(libs.androidx.compose.bom)); androidTestImplementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.compose); implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.core); implementation(libs.androidx.core.splashscreen); implementation(libs.androidx.activity); implementation(libs.androidx.lifecycle.runtime); implementation(libs.androidx.lifecycle.viewmodel)
    /* Compiles the baseline profile in src/main/baseline-prof.txt and installs it
     * after updates; without it the profile only helps fresh installs. */
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.navigation.compose); implementation(libs.androidx.paging.runtime); implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.datastore); implementation(libs.androidx.room.runtime); implementation(libs.androidx.room.ktx); kapt(libs.androidx.room.compiler)
    implementation(libs.androidx.work); implementation(libs.androidx.hilt.work); implementation(libs.androidx.hilt.navigation)
    implementation(libs.hilt.android); kapt(libs.hilt.compiler)
    /* @HiltWorker: without this processor WorkManager falls back to its default
     * factory and cannot construct UploadWorker (NoSuchMethodException). */
    kapt(libs.androidx.hilt.compiler)
    implementation(libs.retrofit); implementation(libs.retrofit.serialization); implementation(libs.okhttp); implementation(libs.okhttp.logging); implementation(libs.serialization.json)
    implementation(libs.coil.compose); implementation(libs.coil.network); implementation(libs.coroutines)
    /* AnimatedVectorDrawable XML (res/drawable/avd_*.xml) played from Compose. Version comes from the BOM. */
    implementation("androidx.compose.animation:animation-graphics")
    if (fcmConfigured) {
        implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
        implementation("com.google.firebase:firebase-messaging")
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
}
