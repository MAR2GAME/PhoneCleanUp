import java.util.Properties
import org.gradle.kotlin.dsl.annotationProcessor
import org.gradle.kotlin.dsl.implementation

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20"
}
val localAdvProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.isFile) {
        localPropertiesFile.inputStream().use(::load)
    }
}

fun Project.advStringProperty(name: String, fallback: String): String {
    val fromGradle = providers.gradleProperty(name).orNull
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val fromEnv = providers.environmentVariable(name).orNull
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLocal = localAdvProperties.getProperty(name)
    return fromLocal?.takeIf { it.isNotBlank() } ?: fallback
}

fun Project.advStringProperty(propertyName: String, envName: String, fallback: String): String {
    val fromGradle = providers.gradleProperty(propertyName).orNull
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val fromLocal = localAdvProperties.getProperty(propertyName)
    if (!fromLocal.isNullOrBlank()) return fromLocal
    val fromEnv = providers.environmentVariable(envName).orNull
    if (!fromEnv.isNullOrBlank()) return fromEnv
    val fromLegacyLocal = localAdvProperties.getProperty(envName)
    return fromLegacyLocal?.takeIf { it.isNotBlank() } ?: fallback
}

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")}\""


android {
    namespace = "com.cleanphone.freespace.utility"
    compileSdk = 36
    packagingOptions {
        merge ("META-INF/DEPENDENCIES")
    }
    defaultConfig {
        applicationId = "com.cleanphone.freespace.utility"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                cppFlags("-std=c++11 -O2 -fvisibility=hidden")
                abiFilters.addAll(listOf("arm64-v8a"))
                arguments("-DCMAKE_BUILD_TYPE=Release")
            }
        }
        ndk {
            abiFilters.addAll(listOf("arm64-v8a"))
        }
        val advAdmobAppId = advStringProperty("ADV_ADMOB_APP_ID", "ca-app-pub-3106003114382927~1492269938")
        buildConfigField("String", "ADV_ADMOB_APP_ID", advAdmobAppId.asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_BANNER_ID", advStringProperty("ADV_ADMOB_BANNER_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_INTERSTITIAL_ID", advStringProperty("ADV_ADMOB_INTERSTITIAL_ID", "ca-app-pub-3106003114382927/8987616577").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_OPEN_ID", advStringProperty("ADV_ADMOB_OPEN_ID", "ca-app-pub-3106003114382927/1408775389").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_NATIVE_ID", advStringProperty("ADV_ADMOB_NATIVE_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_ADMOB_REWARDED_ID", advStringProperty("ADV_ADMOB_REWARDED_ID", "").asBuildConfigString())
        buildConfigField("String", "ADV_DEFAULT_TOPIC", advStringProperty("ADV_DEFAULT_TOPIC", "phone-cleanup-debug").asBuildConfigString())
        buildConfigField("String", "ADV_AD_DEBUG_OVERRIDE_MODE", advStringProperty("adv.adDebugOverride.mode", "ADV_AD_DEBUG_OVERRIDE_MODE", "POLICY").asBuildConfigString())

        val advFacebookAppId = advStringProperty("ADV_FACEBOOK_APP_ID", "")
        val advFacebookClientToken = advStringProperty("ADV_FACEBOOK_CLIENT_TOKEN", "")
        buildConfigField("String", "ADV_FACEBOOK_APP_ID", advFacebookAppId.asBuildConfigString())
        buildConfigField("String", "ADV_FACEBOOK_CLIENT_TOKEN", advFacebookClientToken.asBuildConfigString())
        buildConfigField(
            "String",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY",
            advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY", "").asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID",
            advStringProperty("ADV_REMOTE_CONFIG_ENCRYPTION_KEY_ID", "rc_phonecleanup_freespace_20260806").asBuildConfigString(),
        )
        manifestPlaceholders["advAdmobAppId"] = advAdmobAppId
        manifestPlaceholders["advFacebookAppId"] = advFacebookAppId
        manifestPlaceholders["advFacebookClientToken"] = advFacebookClientToken
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    val releaseStoreFile = localAdvProperties.getProperty("RELEASE_STORE_FILE")
        ?.takeIf { it.isNotBlank() }
        ?.let(::file)
    val releaseStorePassword = localAdvProperties.getProperty("RELEASE_STORE_PASSWORD")
        ?.takeIf { it.isNotBlank() }
    val releaseKeyAlias = localAdvProperties.getProperty("RELEASE_KEY_ALIAS")
        ?.takeIf { it.isNotBlank() }
    val releaseKeyPassword = localAdvProperties.getProperty("RELEASE_KEY_PASSWORD")
        ?.takeIf { it.isNotBlank() }

    signingConfigs {
        create("release") {
            if (releaseStoreFile != null && releaseStorePassword != null &&
                releaseKeyAlias != null && releaseKeyPassword != null
            ) {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null && releaseStorePassword != null &&
                releaseKeyAlias != null && releaseKeyPassword != null
            ) {
                signingConfig = signingConfigs.getByName("release")
            }
            isDebuggable=false
            isMinifyEnabled = true
            isShrinkResources=true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.glide)
    annotationProcessor(libs.compiler)
    implementation(libs.baserecyclerviewadapterhelper)
    api(libs.androidx.lifecycle.extensions)
    api(libs.androidx.lifecycle.viewmodel)
    api(libs.androidx.lifecycle.livedata)
    implementation(libs.eventbus)
    implementation(libs.androidx.databinding.runtime)
    implementation("com.pdffox:advertise:1.0.12")
    implementation(libs.app.update)
    implementation(libs.app.update.ktx)
    implementation(libs.roundedimageview)
    implementation(libs.photoview)
    implementation(libs.gson)
    implementation(libs.lottie)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics-ndk")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.google.android.play:integrity:1.6.0")
}
