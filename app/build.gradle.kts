import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
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

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n")}\""


android {
    namespace = "com.cleanphone.freespace.utility"
    compileSdk {
        version = release(37) { minorApiLevel = 0 }
    }
    ndkVersion = "27.0.12077973"
    packaging {
        resources.merges += "META-INF/DEPENDENCIES"
    }
    defaultConfig {
        applicationId = "com.cleanphone.freespace.utility"
        minSdk = 29
        targetSdk = 36
        versionCode = 9
        versionName = "1.1.9"
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
        manifestPlaceholders["advAdmobAppId"] = advAdmobAppId
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
            optimization {
                enable = true
                keepRules { includeDefault = true }
            }
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
    implementation(libs.mar2.sdk.core)
    implementation(libs.mar2.sdk.impl)
    debugImplementation(libs.mar2.sdk.debug)
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
